package no.nav.rapids_and_rivers.cli

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.lang.RuntimeException

class JsonRiver(rapids: RapidsCliApplication) : MessageListener {
    private val listeners = mutableListOf<JsonValidationSuccessListener>()
    private val errorListeners = mutableListOf<JsonValidationErrorListener>()
    private val validations = mutableListOf<JsonValidation>()
    private val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    init {
        rapids.register(this)
    }

    fun register(listener: JsonMessageListener): JsonRiver {
        onMessage(listener)
        onError(listener)
        return this
    }

    fun prerequisite(validation: JsonValidation): JsonRiver {
        validations.add(PrerequisiteValidation(validation))
        return this
    }

    fun validate(validation: JsonValidation): JsonRiver {
        validations.add(validation)
        return this
    }

    fun onMessage(listener: JsonValidationSuccessListener): JsonRiver {
        listeners.add(listener)
        return this
    }

    fun onError(listener: JsonValidationErrorListener): JsonRiver {
        errorListeners.add(listener)
        return this
    }

    fun unregister(listener: JsonMessageListener): JsonRiver {
        unregister(listener as JsonValidationSuccessListener)
        unregister(listener as JsonValidationErrorListener)
        return this
    }

    fun unregister(listener: JsonValidationSuccessListener): JsonRiver {
        listeners.remove(listener)
        return this
    }

    fun unregister(listener: JsonValidationErrorListener): JsonRiver {
        errorListeners.remove(listener)
        return this
    }

    override fun onMessage(record: ConsumerRecord<String, String>) {
        val node = parseJson(record.value()) ?: return
        val errors = mutableListOf<String>()
        try {
            if (hasErrors(record, node, errors)) return onError(record, node, errors)
        } catch (err: RuntimeException) { return onSevereError(record, node, errors) }
        listeners.onEach { it.onMessage(record, node) }
    }

    private fun hasErrors(record: ConsumerRecord<String, String>, node: JsonNode, errors: MutableList<String>): Boolean {
        return validations.filterNot {
            val reasons = mutableListOf<String>()
            it.validate(record, node, reasons).also { validated ->
                if (!validated) {
                    if (reasons.isEmpty()) errors.add("Unknown reason")
                    else errors.addAll(reasons)
                }
            }
        }.isNotEmpty()
    }

    private fun parseJson(message: String) =
        try { mapper.readTree(message) } catch (err: JsonProcessingException) { /* ignore invalid json */ null }

    private fun onSevereError(record: ConsumerRecord<String, String>, node: JsonNode, reasons: List<String>) {
        // noop
    }

    private fun onError(record: ConsumerRecord<String, String>, node: JsonNode, reasons: List<String>) {
        errorListeners.forEach { it.onError(record, node, reasons) }
    }

    private class PrerequisiteValidation(private val validation: JsonValidation) : JsonValidation {
        override fun validate(record: ConsumerRecord<String, String>, node: JsonNode, reasons: MutableList<String>): Boolean {
            if (validation.validate(record, node, reasons)) return true
            throw RuntimeException("Failed prerequisite validation")
        }
    }

    fun interface JsonValidation {
        fun validate(record: ConsumerRecord<String, String>, node: JsonNode, reasons: MutableList<String>): Boolean
    }

    fun interface JsonValidationErrorListener {
        fun onError(record: ConsumerRecord<String, String>, node: JsonNode, reasons: List<String>)
    }

    fun interface JsonValidationSuccessListener {
        fun onMessage(record: ConsumerRecord<String, String>, node: JsonNode)
    }

    interface JsonMessageListener : JsonValidationErrorListener, JsonValidationSuccessListener
}
