val junitJupiterVersion = "5.11.3"
val jacksonVersion = "2.18.1"
val kafkaVersion = "3.9.0"
val logbackClassicVersion = "1.5.12"
val logbackEncoderVersion = "8.0"

group = "com.github.navikt"
version = properties["version"] ?: "local-build"

plugins {
    kotlin("jvm") version "2.1.20"
    id("java")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaVersion")

    api("ch.qos.logback:logback-classic:$logbackClassicVersion")
    api("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion") {
        exclude("com.fasterxml.jackson.core")
    }

    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("21"))
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

val githubUser: String? by project
val githubPassword: String? by project

publishing {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/navikt/rapids-and-rivers-cli")
            credentials {
                username = githubUser
                password = githubPassword
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {

            pom {
                name.set("rapids-rivers-cli")
                description.set("Rapids and Rivers CLI")
                url.set("https://github.com/navikt/rapids-and-rivers-cli")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/navikt/rapids-and-rivers-cli.git")
                    developerConnection.set("scm:git:https://github.com/navikt/rapids-and-rivers-cli.git")
                    url.set("https://github.com/navikt/rapids-and-rivers-cli")
                }
            }
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
}
