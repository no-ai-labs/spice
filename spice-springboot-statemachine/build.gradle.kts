plugins {
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot") version "3.5.3"
    id("maven-publish")
}

dependencies {
    api(project(":spice-core"))
    api(project(":spice-springboot"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.statemachine:spring-statemachine-core:3.3.0")
    implementation("org.springframework.statemachine:spring-statemachine-data-redis:3.3.0")

    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("io.lettuce:lettuce-core")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.5.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    enabled = false
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

publishing {
    publications {
        create<MavenPublication>("statemachine") {
            from(components["java"])
            groupId = "io.github.noailabs"
            artifactId = "spice-springboot-statemachine"
            version = "0.9.5"

            pom {
                name.set("Spice Spring Boot State Machine Extension")
                description.set("HITL automation, retry, persistence, events, and visualization for Spice GraphRunner workflows")
                url.set("https://github.com/no-ai-labs/spice")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("spice-team")
                        name.set("Spice Framework Team")
                        email.set("veryverybigdog@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/no-ai-labs/spice.git")
                    developerConnection.set("scm:git:ssh://github.com/no-ai-labs/spice.git")
                    url.set("https://github.com/no-ai-labs/spice")
                }
            }
        }
    }
}
