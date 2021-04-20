package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.RuntimeException
import java.util.*

internal class JsonRiverTest {

    private val testConfig = object : Config {
        override fun producerConfig(properties: Properties) = properties
        override fun consumerConfig(groupId: String, properties: Properties) = properties
    }

    private lateinit var cli: RapidsCliApplication
    private lateinit var river: JsonRiver

    @BeforeEach
    fun setup() {
        cli = RapidsCliApplication(ConsumerProducerFactory(testConfig))
        river = JsonRiver(cli)
    }

    @Test
    fun `ignorerer jsonerror`() {
        var errors: List<String>? = null
        var validated = false

        river.validate { _, _, _ -> true }
        river.validate { _, _, _ -> false }
        river.onError { _, _, reasons -> errors = reasons }
        river.onMessage { _, _ -> validated = true }
        river.onMessage(ConsumerRecord("topic", 1, 1, "key", "invalid json"))

        assertFalse(validated)
        assertNull(errors)
    }

    @Test
    fun `lager ikke errors for validering som er ok`() {
        var errors: List<String>? = null
        var validated = false

        river.validate { _, _, _ -> true }
        river.validate { _, _, _ -> false }
        river.onError { _, _, reasons -> errors = reasons }
        river.onMessage { _, _ -> validated = true }
        river.onMessage(ConsumerRecord("topic", 1, 1, "key", "{}"))

        assertFalse(validated)
        assertNotNull(errors)
        assertEquals(1, errors!!.size)
    }

    @Test
    fun `stopper validering ved exception`() {
        val validations = mutableListOf<String>()
        var errorCalled = false
        river.validate { _, _, _ -> throw RuntimeException("Failed requirement") }
        river.validate { _, _, _ -> false.also { validations.add("Validation 2") } }
        river.onError { _, _, reasons -> errorCalled = true }
        river.onMessage(ConsumerRecord("topic", 1, 1, "key", "{}"))

        assertTrue(validations.isEmpty())
        assertFalse(errorCalled)
    }

    @Test
    fun `stopper validering dersom forutsetning ikke er møtt`() {
        val validations = mutableListOf<String>()
        var errorCalled = false
        river.prerequisite { _, _, _ -> false.also { validations.add("Validation 1") } }
        river.validate { _, _, _ -> false.also { validations.add("Validation 2") } }
        river.onError { _, _, reasons -> errorCalled = true }
        river.onMessage(ConsumerRecord("topic", 1, 1, "key", "{}"))

        assertEquals("Validation 1", validations.joinToString())
        assertFalse(errorCalled)
    }
}
