package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.consumer.ConsumerRecord

fun interface MessageListener {
    fun onMessage(record: ConsumerRecord<String, String>)
}
