plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "io.github.noailabs"
version = "0.9.4"

repositories {
    mavenCentral()
}

dependencies {
    // Spice Framework
    api(project(":spice-core"))

    // RDF4J (AWS Neptune official support)
    implementation("org.eclipse.rdf4j:rdf4j-client:4.3.10")
    implementation("org.eclipse.rdf4j:rdf4j-repository-sparql:4.3.10")
    implementation("org.eclipse.rdf4j:rdf4j-repository-api:4.3.10")
    implementation("org.eclipse.rdf4j:rdf4j-model:4.3.10")

    // Handlebars template engine (KAI-Core compatible)
    implementation("com.github.jknack:handlebars:4.3.1")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Optional: AWS SDK for Neptune IAM authentication
    compileOnly("software.amazon.awssdk:auth:2.20.0")
    compileOnly("software.amazon.awssdk:neptune:2.20.0")

    // Test dependencies
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "io.github.noailabs"
            artifactId = "spice-extensions-sparql"
            version = "0.9.4"

            pom {
                name.set("Spice Extensions - SPARQL")
                description.set("SPARQL extension for Spice Framework with RDF4J and Handlebars support")
                url.set("https://github.com/no-ai-labs/spice")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("noailabs")
                        name.set("No AI Labs")
                        email.set("hello@noailabs.io")
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
