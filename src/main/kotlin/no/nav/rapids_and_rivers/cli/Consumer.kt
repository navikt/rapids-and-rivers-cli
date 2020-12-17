package no.nav.rapids_and_rivers.cli

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

private val log = LoggerFactory.getLogger("no.nav.rapids_and_rivers.cli.seekTo")

fun KafkaConsumer<*, *>.seekTo(topic: String, time: LocalDateTime) {
    val partitions = partitionsFor(topic)
        .map { TopicPartition(topic, it.partition()) }
        .also {
            assign(it)
            while (poll(Duration.ofSeconds(1)).count() == 0) {
                log.info("Waiting for parition assignment")
            }
        }
    val endOffsets = endOffsets(partitions)

    partitions.map { it to time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.toMap()
        .let { offsetsForTimes(it) }
        .mapValues { (topicPartition, offsetAndTimestamp) -> offsetAndTimestamp?.offset() ?: endOffsets.getValue(topicPartition) }
        .onEach { (topicPartition, offset) -> log.info("Setter offset for partisjon $topicPartition til $offset") }
        .forEach { (topicPartition, offset) -> seek(topicPartition, offset) }

    commitSync()
    unsubscribe()
}
