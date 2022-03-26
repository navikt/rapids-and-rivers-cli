package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
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

    private var partitionsAssignedFirstTime: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit = { _, _ -> }
    private var partitionsAssigned: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit = { _, _ -> }
    private var partitionsRevoked: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit = { _, _ -> }
    private var onShutdown: (KafkaConsumer<String, String>) -> Unit = { consumer ->
        if (commitOffsetsOnShutdown) tryAndLog("failed to commit offsets") { consumer.commitSync(Duration.ofSeconds(1)) }
    }

    private val defaultConsumerProperties = Properties().apply {
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    }
    private lateinit var consumer: KafkaConsumer<String, String>
    private var commitOffsetsOnShutdown = false

    init {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            log.error("Uncaught exception: ${throwable.message}", throwable)
        }
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    fun register(messageListener: MessageListener): RapidsCliApplication {
        listeners.add(messageListener)
        return this
    }

    fun unregister(messageListener: MessageListener): RapidsCliApplication {
        listeners.remove(messageListener)
        return this
    }

    fun commitOffsetsOnShutdown(answer: Boolean): RapidsCliApplication {
        this.commitOffsetsOnShutdown = answer
        return this
    }

    fun stop() {
        if (!running.getAndSet(false)) return log.error("Already in process of shutting down")
        log.info("Received shutdown signal. Waiting 10 seconds for app to shutdown gracefully")
        if (this::consumer.isInitialized) consumer.wakeup()
        shutdown.await(10, TimeUnit.SECONDS)
    }

    fun partitionsAssignedFirstTime(callback: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit): RapidsCliApplication {
        partitionsAssignedFirstTime = callback
        return this
    }

    fun partitionsAssigned(callback: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit): RapidsCliApplication {
        partitionsAssigned = callback
        return this
    }

    fun partitionsRevoked(callback: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit): RapidsCliApplication {
        partitionsRevoked = callback
        return this
    }

    fun onShutdown(callback: (KafkaConsumer<String, String>) -> Unit): RapidsCliApplication {
        onShutdown = callback
        return this
    }

    fun start(groupId: String, topics: List<String>, properties: Properties = Properties(), configure: (KafkaConsumer<String, String>) -> Unit = {}) {
        if (running.getAndSet(true)) throw IllegalStateException("Already running")
        val props = Properties(defaultConsumerProperties).apply { putAll(properties) }
        consumer = factory.createConsumer(groupId, props).apply {
            use { consumer ->
                configure(consumer)
                consumer.subscribe(topics, RebalanceListener(consumer, partitionsAssignedFirstTime, partitionsAssigned, partitionsRevoked))
                try {
                    while (running.get())
                        consumer.poll(Duration.ofSeconds(1)).forEach { record ->
                            try { listeners.onEach { it.onMessage(record) } }
                            catch (err: Exception) { /* ignore listener error */ }
                        }
                } catch (err: WakeupException) {
                    log.info("Exiting consumer after ${if (!running.get()) "receiving shutdown signal" else "being interrupted by someone" }")
                }
                onShutdown(consumer)
                tryAndLog("failed to unsubscribe") { consumer.unsubscribe() }
                shutdown.countDown()
            }
        }
    }

    private fun tryAndLog(message: String, block: () -> Unit) {
        try { block() }
        catch (err: Exception) { log.warn(message, err)}
    }

    private class RebalanceListener(
        private val consumer: KafkaConsumer<String, String>,
        private val partitionsAssignedFirstTime: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit,
        private val assignCallback: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit,
        private val revokeCallback: (KafkaConsumer<String, String>, Collection<TopicPartition>) -> Unit
    ) : ConsumerRebalanceListener {
        private val partitionsSeenBefore = mutableSetOf<TopicPartition>()

        override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
            val new = partitions.filter { partitionsSeenBefore.add(it) }
            if (new.isNotEmpty()) partitionsAssignedFirstTime(consumer, new)
            assignCallback(consumer, partitions)
        }

        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
            revokeCallback(consumer, partitions)
        }
    }
}
