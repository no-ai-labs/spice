plugins {
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot") version "3.5.7"
    id("maven-publish")
}

dependencies {
    api(project(":spice-core"))
    api(project(":spice-springboot"))

    implementation("org.springframework.boot:spring-boot-starter:3.5.7")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.7")
    implementation("org.springframework.statemachine:spring-statemachine-core:4.0.0")
    implementation("org.springframework.statemachine:spring-statemachine-data-redis:4.0.0")

    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive:3.5.7")
    implementation("io.lettuce:lettuce-core:6.5.2.RELEASE")

    implementation("org.springframework.boot:spring-boot-starter-actuator:3.5.7")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.3")

    implementation("org.springframework.kafka:spring-kafka:3.3.3")
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.5.7")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.7")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
    archiveClassifier.set("")  // Ensure no "-plain" suffix
}

publishing {
    publications {
        create<MavenPublication>("statemachine") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "spice-springboot-statemachine"
            version = project.version.toString()

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
                        id.set("no-ai-labs")
                        name.set("Spice Framework Team")
                        email.set("human@noailabs.ai")
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
