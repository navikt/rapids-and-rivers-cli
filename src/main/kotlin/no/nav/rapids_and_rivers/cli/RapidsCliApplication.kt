package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.lang.IllegalStateException
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RapidsCliApplication(private val factory: ConsumerProducerFactory) {

    constructor(config: Config) : this(ConsumerProducerFactory(config))

    private val log = LoggerFactory.getLogger(this::class.java)
    private val listeners = mutableListOf<MessageListener>()
    private val shutdown = CountDownLatch(1)
    private val running = AtomicBoolean(false)

    private val defaultConsumerProperties = Properties().apply {
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    }
    private lateinit var consumer: KafkaConsumer<String, String>

    init {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            log.error("Uncaught exception: ${throwable.message}", throwable)
        }
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    fun register(messageListener: MessageListener) {
        listeners.add(messageListener)
    }

    fun unregister(messageListener: MessageListener) {
        listeners.remove(messageListener)
    }

    fun stop() {
        if (!running.getAndSet(false)) return log.error("Already in process of shutting down")
        log.info("Received shutdown signal. Waiting 10 seconds for app to shutdown gracefully")
        if (this::consumer.isInitialized) consumer.wakeup()
        shutdown.await(10, TimeUnit.SECONDS)
    }

    fun start(groupId: String, topics: List<String>, properties: Properties = Properties(), configure: (KafkaConsumer<String, String>) -> Unit = {}) {
        if (running.getAndSet(true)) throw IllegalStateException("Already running")
        val props = Properties(defaultConsumerProperties).apply { putAll(properties) }
        consumer = factory.createConsumer(groupId, props).apply {
            use { consumer ->
                configure(consumer)
                consumer.subscribe(topics)
                try {
                    while (running.get())
                        consumer.poll(Duration.ofSeconds(1)).forEach { record ->
                            try { listeners.onEach { it.onMessage(record) } }
                            catch (err: Exception) { /* ignore listener error */ }
                        }
                } catch (err: WakeupException) {
                    log.info("Exiting consumer after ${if (!running.get()) "receiving shutdown signal" else "being interrupted by someone" }")
                }
                tryAndLog("failed to commit offsets") { consumer.commitSync(Duration.ofSeconds(1)) }
                tryAndLog("failed to unsubscribe") { consumer.unsubscribe() }
                shutdown.countDown()
            }
        }
    }

    private fun tryAndLog(message: String, block: () -> Unit) {
        try { block() }
        catch (err: Exception) { log.warn(message, err)}
    }
}
