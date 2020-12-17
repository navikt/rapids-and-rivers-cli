# Rapids and rivers CLI

Hvor mange biblioteker kan man lage for å konsumere fra Kafka, 'a?
- `n + 1`

## Eksempel-app

```kotlin

private val config = Config(
    brokers = listOf("broker.url"),
    username = "username",
    password = "pw",
    truststorePath = "/path/to/truststore",
    truststorePw = "trustorepw"
)

fun main() {
    val topics = listOf("my-cool-topic")
    RapidsCliApplication(config).apply {
        // parses every message as json
        JsonRiver(this).apply {
            // listens only on json messages
            val typer = listOf("my_cool_event", "my_other_event")
            validate { _, node -> node.hasNonNull("@event_name") }
            validate { _, node -> node.path("@event_name").isTextual }
            validate { _, node -> node.path("@event_name").asText() !in typer }
            onSuccess { _, node -> println(node.toString()) }
            onError { _, _ -> /*println("Message did not pass validation!")*/ }
        }
        // listens on every "raw" string message
        register(printStatistics())
    }.start("my-cool-consumer", topics) { consumer ->
        // seek to a particular time
        topics.onEach { topic -> consumer.seekTo(topic, LocalDateTime.now().minusHours(1)) }
    }
}

// print a message count for each partition on every message
private fun printStatistics(): (ConsumerRecord<String, String>) -> Unit {
    val messageCounts = mutableMapOf<Int, Long>()
    return fun (record: ConsumerRecord<String, String>) {
        val partition = record.partition()
        messageCounts.increment(partition)
        println(messageCounts.toString(partition))
    }
}

private fun <K> MutableMap<K, Long>.increment(key: K) =
    (getOrDefault(key, 0) + 1).also { this[key] = it }

private fun <K : Comparable<K>> Map<K, Long>.toString(selectedKey: K) =
    map { it.key to it.value }
        .sortedBy(Pair<K, *>::first)
        .joinToString(separator = "  ") { (key, count) ->
            val marker = if (key == selectedKey) "*" else " "
            val countString = count.toString().padStart(5, ' ')
            "[$marker$countString]"
        }

```


## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod.
