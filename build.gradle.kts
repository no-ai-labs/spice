plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    kotlin("plugin.spring") version "2.2.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("signing")
}

allprojects {
    group = "io.github.no-ai-labs"
    version = "0.1.1"
    
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
}