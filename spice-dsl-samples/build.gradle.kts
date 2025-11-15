plugins {
    kotlin("jvm")
    application
}

group = "io.github.no-ai-labs"
version = "1.0.0-alpha2"

repositories {
    mavenCentral()
}

dependencies {
    // Core Spice Framework
    implementation(project(":spice-core"))

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("net.objecthunter:exp4j:0.4.8")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.noailabs.spice.samples.SimpleToolRunner")
}

// Modern Components Test Task
tasks.register<JavaExec>("testModern") {
    group = "application"
    description = "Run Modern Components Test"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.noailabs.spice.samples.ModernComponentsRunnerKt")
}

// Real API Test Task
tasks.register<JavaExec>("testRealAPI") {
    group = "application"
    description = "Run real API tests with actual API keys"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.noailabs.spice.samples.RealApiTestRunnerKt")
}

// Detailed API Debug Task
tasks.register<JavaExec>("debugAPI") {
    group = "application"
    description = "Run detailed API debugging with enhanced logging"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.noailabs.spice.samples.DetailedApiTestRunnerKt")
}

// Final Integration Test Task
tasks.register<JavaExec>("finalTest") {
    group = "application"
    description = "Run final integration test with production configuration"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.noailabs.spice.samples.FinalApiTestRunnerKt")
}

// Simple DSL Test Task
tasks.register<JavaExec>("testDSL") {
    group = "application"
    description = "Run simple DSL functionality test"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.noailabs.spice.samples.SimpleDSLTestKt")
}

// Quick DSL Test Task  
tasks.register<JavaExec>("quickTest") {
    group = "application"
    description = "Run quick Ultimate DSL test"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.noailabs.spice.samples.QuickDSLTestKt")
} 
