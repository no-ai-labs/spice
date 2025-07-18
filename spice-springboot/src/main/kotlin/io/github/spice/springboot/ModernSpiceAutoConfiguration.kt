package io.github.spice.springboot

import io.github.spice.*
import io.github.spice.dsl.*
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Modern Spice Framework Spring Boot Auto Configuration
 */
@AutoConfiguration
@EnableConfigurationProperties(SpiceProperties::class)
class ModernSpiceAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["spice.openai.enabled"], havingValue = "true")
    fun openaiAgent(properties: SpiceProperties): Agent {
        return buildAgent {
            id = "openai-agent"
            name = "OpenAI GPT Agent"
            description = "OpenAI GPT agent for Spring Boot"
            
            handle { comm ->
                when(comm.type) {
                    CommType.TEXT -> {
                        comm.reply(
                            content = "I'm a GPT-based assistant. How can I help you?",
                            from = id
                        )
                    }
                    else -> {
                        comm.reply(
                            content = "I received a ${comm.type} message",
                            from = id
                        )
                    }
                }
            }
        }
    }

    @Bean 
    @ConditionalOnProperty(name = ["spice.anthropic.enabled"], havingValue = "true")
    fun claudeAgent(properties: SpiceProperties): Agent {
        return buildAgent {
            id = "claude-agent"
            name = "Claude Agent"
            description = "Anthropic Claude agent for Spring Boot"
            
            handle { comm ->
                when(comm.type) {
                    CommType.TEXT -> {
                        comm.reply(
                            content = "I'm Claude, an AI assistant. How can I help you?",
                            from = id
                        )
                    }
                    else -> {
                        comm.reply(
                            content = "I received a ${comm.type} message",
                            from = id
                        )
                    }
                }
            }
        }
    }

    @Bean
    fun spiceRegistry(): Map<String, Agent> {
        return mutableMapOf()
    }
}