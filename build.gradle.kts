val junitJupiterVersion = "5.10.1"
val jacksonVersion = "2.16.1"
val kafkaVersion = "3.6.1"

group = "com.github.navikt"
version = properties["version"] ?: "local-build"

plugins {
    kotlin("jvm") version "1.9.22"
    id("java")
    id("maven-publish")
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaVersion")

    api("ch.qos.logback:logback-classic:1.4.14")
    api("net.logstash.logback:logstash-logback-encoder:7.4") {
        exclude("com.fasterxml.jackson.core")
    }

    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "21"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "21"
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    withType<Wrapper> {
        gradleVersion = "8.5"
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
