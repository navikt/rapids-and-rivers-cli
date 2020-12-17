package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.io.File
import java.util.*

class Config(
    private val brokers: List<String>,
    private val username: String,
    private val password: String,
    private val truststorePath: String,
    private val truststorePw: String
) {

    internal fun createConsumer(groupId: String, properties: Properties = Properties()) =
        KafkaConsumer(consumerConfig(groupId, properties), StringDeserializer(), StringDeserializer()).also {
            Runtime.getRuntime().addShutdownHook(Thread {
                it.wakeup()
            })
        }

    fun createProducer(properties: Properties = Properties()) =
        KafkaProducer(producerConfig(properties), StringSerializer(), StringSerializer()).also {
            Runtime.getRuntime().addShutdownHook(Thread {
                it.close()
            })
        }

    private fun producerConfig(properties: Properties) = Properties().apply {
        putAll(kafkaBaseConfig())
        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
        putAll(properties)
    }

    private fun consumerConfig(groupId: String, properties: Properties) = Properties().apply {
        putAll(kafkaBaseConfig())
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        putAll(properties)
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    }

    private fun kafkaBaseConfig() = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokers.joinToString())
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
        put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";")
        val path = File(truststorePath).absolutePath
        println("Configured to use truststore '$path'")
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, path)
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePw)
    }
}
