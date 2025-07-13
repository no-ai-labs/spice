plugins {
    kotlin("plugin.serialization")
    id("maven-publish")
}

sourceSets {
    main {
        kotlin {
            exclude("**/SpicePlugin.kt")
            exclude("**/ToolChain.kt") 
            exclude("**/toolhub/**")
        }
    }
}

dependencies {
    // Kotlin 표준 라이브러리 명시적 추가
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    
    // 🌶️ Spice Core Dependencies - Framework 독립적
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Google Cloud 인증 라이브러리 (VertexAgent용)
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.auth:google-auth-library-credentials:1.19.0")
    
    // 로깅 (SLF4J API만, 구현체는 사용하는 프레임워크에서 제공)
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    
    // 테스트
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.7")
}

// 루트 build.gradle.kts에서 공통 설정됨

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("Spice Core")
                description.set("JVM-based Multi-Agent Orchestration Framework - The Spice of Workflows")
                url.set("https://github.com/spice-framework/spice")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("spice-team")
                        name.set("Spice Framework Team")
                        email.set("team@spice.dev")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/spice-framework/spice.git")
                    developerConnection.set("scm:git:ssh://github.com/spice-framework/spice.git")
                    url.set("https://github.com/spice-framework/spice")
                }
            }
        }
    }
} 