plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    kotlin("plugin.spring") version "2.2.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("signing")
}

allprojects {
    group = "io.github.noailabs"
    version = "0.9.5"

    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }

    }

}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    
    tasks.withType<Test> {
        useJUnitPlatform()
    }

    plugins.withId("maven-publish") {
        extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "private"
                    url = uri("http://localhost:8080/repository/maven-releases/")
                    credentials {
                        username = findProperty("repoUser") as String? ?: System.getenv("REPO_USER")
                        password = findProperty("repoPass") as String? ?: System.getenv("REPO_PASS")
                    }
                }
            }
        }
    }
}
