plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    kotlin("plugin.spring") version "2.2.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("signing")
}

allprojects {
    group = "com.github.no-ai-labs.spice"
    version = "1.7.1"

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
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/no-ai-labs/spice")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String? ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String? ?: ""
                    }
                }
                maven {
                    name = "Nexus"
                    val releasesRepoUrl = uri("https://registry.kjai.kr/repository/maven-releases")
                    val snapshotsRepoUrl = uri("https://registry.kjai.kr/repository/maven-snapshots")
                    url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                    credentials {
                        username = findProperty("nexusUsername") as String? ?: System.getenv("NEXUS_USERNAME")
                        password = findProperty("nexusPassword") as String? ?: System.getenv("NEXUS_PASSWORD")
                    }
                }
            }
        }
    }
}
