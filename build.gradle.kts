plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    kotlin("plugin.spring") version "2.2.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("signing")
}

allprojects {
    group = "io.github.noailabs"
    version = "1.1.0"

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
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "Nexus"
                    val releasesRepoUrl = uri("https://dev.questy.life/nexus/repository/maven-releases")
                    val snapshotsRepoUrl = uri("https://dev.questy.life/nexus/repository/maven-snapshots")
                    url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                    isAllowInsecureProtocol = true
                    credentials {
                        username = findProperty("nexusUsername") as String? ?: System.getenv("NEXUS_USERNAME")
                        password = findProperty("nexusPassword") as String? ?: System.getenv("NEXUS_PASSWORD")
                    }
                }
            }
        }
    }
}
