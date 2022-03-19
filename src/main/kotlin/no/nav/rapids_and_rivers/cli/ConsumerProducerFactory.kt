package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

class ConsumerProducerFactory(private val config: Config) {
    internal fun createConsumer(groupId: String, properties: Properties = Properties()) =
        KafkaConsumer(config.consumerConfig(groupId, properties), StringDeserializer(), StringDeserializer()).also {
            Runtime.getRuntime().addShutdownHook(Thread {
                it.wakeup()
            })
        }

    fun createProducer(properties: Properties = Properties()) =
        KafkaProducer(config.producerConfig(properties), StringSerializer(), StringSerializer()).also {
            Runtime.getRuntime().addShutdownHook(Thread {
                it.close()
            })
        }

    fun adminClient(properties: Properties = Properties()) = AdminClient.create(config.adminConfig(properties))
}
