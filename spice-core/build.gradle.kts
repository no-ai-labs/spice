plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
    jacoco
}

sourceSets {
    main {
        kotlin {
            exclude("**/SpicePlugin.kt")
            exclude("**/ToolChain.kt") 
            exclude("**/toolhub/**")
            exclude("**/PlanningTool.kt")
            exclude("**/VLLMClient.kt")
            exclude("**/GraphvizFlowGenerator.kt")
        }
    }
}

dependencies {
    // Kotlin 표준 라이브러리 명시적 추가
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")

    // 🌶️ Spice Core Dependencies - Framework 독립적
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

    // Public API - exposed to consumers (NodeContext.state uses PersistentMap)
    api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    
    // 🌐 HTTP Client - API 호출용
    implementation("io.ktor:ktor-client-core:2.3.13")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")

    // 📊 OpenTelemetry - Observability
    implementation("io.opentelemetry:opentelemetry-api:1.34.1")
    implementation("io.opentelemetry:opentelemetry-sdk:1.34.1")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.34.1")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.34.1")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.23.1-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.1.0")

    // Redis clients for MQ/Cache/Idempotency backends (exposed as API for Spring Boot integration)
    api("redis.clients:jedis:5.1.2")

    // 📝 Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-api:2.0.9")

    // 🚀 Kafka Support
    implementation("org.apache.kafka:kafka-clients:3.7.1")
    
    // 🧪 Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")

    // Kotest for property-based testing
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")

    // Test fixtures and utilities
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

    // Testcontainers for integration tests
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
    
    // 테스트 메모리 설정
    maxHeapSize = "1g"
    
    // 테스트 타임아웃 설정
    systemProperty("kotlinx.coroutines.debug", "off")
    ignoreFailures = true
}

kotlin {
    jvmToolchain(21)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(false)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }
}

publishing {
    publications {
        create<MavenPublication>("core") {
            from(components["java"])
            groupId = "io.github.noailabs"
            artifactId = "spice-core"
            version = "1.0.0-beta"

            pom {
                name.set("Spice Core")
                description.set("Core utilities and orchestration engine for the Spice LLM Framework")
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
