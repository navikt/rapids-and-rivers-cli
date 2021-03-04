package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.io.File
import java.util.*

class OnPremConfig(
    private val brokers: List<String>,
    private val username: String,
    private val password: String,
    private val truststorePath: String,
    private val truststorePw: String
) : Config {
    companion object {
        val default get() = OnPremConfig(
            brokers = requireNotNull(System.getenv("KAFKA_BOOTSTRAP_SERVERS")) { "Expected KAFKA_BOOTSTRAP_SERVERS" }.split(',').map(String::trim),
            username = "/var/run/secrets/nais.io/service_user/username".readFile(),
            password = "/var/run/secrets/nais.io/service_user/password".readFile(),
            truststorePath = requireNotNull(System.getenv("NAV_TRUSTSTORE_PATH")) { "Expected NAV_TRUSTSTORE_PATH" },
            truststorePw = requireNotNull(System.getenv("NAV_TRUSTSTORE_PASSWORD")) { "Expected NAV_TRUSTSTORE_PASSWORD" }
        )

        private fun String.readFile() =
            File(this).readText(Charsets.UTF_8)
    }

    init {
        check(brokers.isNotEmpty())
        check(username.isNotBlank())
        check(password.isNotBlank())
    }

    override fun producerConfig(properties: Properties) = Properties().apply {
        putAll(kafkaBaseConfig())
        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
        putAll(properties)
    }

    override fun consumerConfig(groupId: String, properties: Properties) = Properties().apply {
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
