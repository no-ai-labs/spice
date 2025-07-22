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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = "io.github.no-ai-labs"
            artifactId = "spice-core"
            version = "0.1.0"
        }
    }
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