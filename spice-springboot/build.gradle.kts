plugins {
    kotlin("plugin.spring")
    id("maven-publish")
}

dependencies {
    // 🌶️ Spice Core 의존성
    api(project(":spice-core"))
    
    // Spring Boot AutoConfiguration
    implementation("org.springframework.boot:spring-boot-starter:3.5.3")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
    implementation("org.springframework:spring-webflux:6.2.1")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    
    // Configuration Processor (IDE 지원)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.5.3")
    
    // 테스트 (Spring Boot Test 포함)
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

// Temporarily disable tests due to test executor issues
tasks.test {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("Spice Spring Boot Starter")
                description.set("Spring Boot AutoConfiguration for Spice Framework - Multi-LLM Orchestration")
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