package no.nav.rapids_and_rivers.cli

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord

class JsonRiver(rapids: RapidsCliApplication) : MessageListener {
    private val listeners = mutableListOf<JsonMessageListener>()
    private val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    init {
        rapids.register(this)
    }

    fun register(listener: JsonMessageListener) {
        listeners.add(listener)
    }

    fun unregister(listener: JsonMessageListener) {
        listeners.remove(listener)
    }

    override fun onMessage(record: ConsumerRecord<String, String>) {
        val node = parseJson(record.value()) ?: return
        listeners.onEach { it.onMessage(record, node) }
    }

    private fun parseJson(message: String) =
        try { mapper.readTree(message) } catch (err: JsonProcessingException) { /* ignore invalid json */ null }

    fun interface JsonMessageListener {
        fun onMessage(record: ConsumerRecord<String, String>, node: JsonNode)
    }
}
