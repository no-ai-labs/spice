plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

sourceSets {
    main {
        kotlin {
            exclude("**/SpicePlugin.kt")
            exclude("**/ToolChain.kt") 
            exclude("**/toolhub/**")
            exclude("**/AgentDocumentGenerator.kt")
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
    
    // 🌐 HTTP Client - API 호출용
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // 🧪 Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = "io.github.spice"
            artifactId = "spice-core"
            version = "1.0.0"
        }
    }
} 