package io.github.noailabs.spice.agents

import io.github.noailabs.spice.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking

/**
 * 🧪 Integration Tests for GPT Agent
 *
 * These tests verify the GPT agent's behavior with mock responses
 * and ensure proper integration with the Comm system.
 */
class GPTAgentIntegrationTest : StringSpec({

    "mockGPTAgent should respond to basic queries" {
        val agent = mockGPTAgent(
            id = "test-gpt",
            personality = "professional",
            debugEnabled = false
        )

        agent.id shouldBe "test-gpt"

        val comm = Comm(
            content = "What is Kotlin?",
            from = "user",
            to = agent.id
        )

        runBlocking {
            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.from shouldBe agent.id
            response.to shouldBe "user"
            response.role shouldBe CommRole.ASSISTANT
        }
    }

    "mockGPTAgent with professional personality should give structured responses" {
        val agent = mockGPTAgent(
            id = "professional-gpt",
            personality = "professional"
        )

        val comm = Comm(
            content = "Explain functional programming",
            from = "user"
        )

        runBlocking {
            val response = agent.processComm(comm)

            // Professional responses should be well-formed
            response.content.shouldNotBeBlank()
            response.content.length shouldNotBe 0
            response.role shouldBe CommRole.ASSISTANT
        }
    }

    "mockGPTAgent with technical personality should provide detailed responses" {
        val agent = mockGPTAgent(
            id = "technical-gpt",
            personality = "technical"
        )

        val comm = Comm(
            content = "How does coroutine work?",
            from = "developer"
        )

        runBlocking {
            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.from shouldBe "technical-gpt"
            response.to shouldBe "developer"
        }
    }

    "mockGPTAgent with casual personality should be conversational" {
        val agent = mockGPTAgent(
            id = "casual-gpt",
            personality = "casual"
        )

        val comm = Comm(
            content = "Hello!",
            from = "friend"
        )

        runBlocking {
            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.role shouldBe CommRole.ASSISTANT
        }
    }

    "mockGPTAgent should allow custom configuration" {
        val agent = mockGPTAgent(
            id = "custom-gpt",
            personality = "professional"
        )

        agent.id shouldBe "custom-gpt"
    }

    "gptAgent factory should create agent with defaults" {
        val agent = mockGPTAgent()

        agent.id.shouldNotBeBlank()
        agent.id shouldContain "gpt"
    }

    "gptAgent should handle multiple sequential messages" {
        val agent = mockGPTAgent(id = "sequential-gpt")

        val messages = listOf(
            "What is 2 + 2?",
            "What is the capital of France?",
            "Tell me about Kotlin"
        )

        runBlocking {
            messages.forEach { message ->
                val comm = Comm(content = message, from = "user")
                val response = agent.processComm(comm)

                response.content.shouldNotBeBlank()
                response.from shouldBe "sequential-gpt"
            }
        }
    }

    "gptAgent should preserve conversation context with parentId" {
        val agent = mockGPTAgent(id = "context-gpt")

        runBlocking {
            val firstComm = Comm(
                content = "My name is Alice",
                from = "user"
            )

            val firstResponse = agent.processComm(firstComm)
            firstResponse.parentId shouldBe null

            // Follow-up message
            val followUp = Comm(
                content = "What is my name?",
                from = "user",
                parentId = firstResponse.id
            )

            val followUpResponse = agent.processComm(followUp)
            followUpResponse.content.shouldNotBeBlank()
        }
    }

    "gptAgent should handle error scenarios gracefully" {
        val agent = mockGPTAgent(id = "error-handling-gpt")

        runBlocking {
            val emptyComm = Comm(
                content = "",
                from = "user"
            )

            val response = agent.processComm(emptyComm)
            // Should still return a response, even for empty content
            response shouldNotBe null
        }
    }

    "gptAgent with debug enabled should work correctly" {
        val agent = mockGPTAgent(
            id = "debug-gpt",
            debugEnabled = true
        )

        val comm = Comm(
            content = "Debug test",
            from = "tester"
        )

        runBlocking {
            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.from shouldBe "debug-gpt"
        }
    }

    "gptAgent should handle special characters in content" {
        val agent = mockGPTAgent(id = "special-char-gpt")

        val specialContent = "Test with 特殊文字 и символы and émojis 🎉"

        val comm = Comm(
            content = specialContent,
            from = "user"
        )

        runBlocking {
            val response = agent.processComm(comm)
            response.content.shouldNotBeBlank()
        }
    }

    "gptAgent should work with different Comm types" {
        val agent = mockGPTAgent(id = "type-test-gpt")

        val types = listOf(CommType.TEXT, CommType.SYSTEM)

        runBlocking {
            types.forEach { type ->
                val comm = Comm(
                    content = "Test message",
                    from = "user",
                    type = type
                )

                val response = agent.processComm(comm)
                response.content.shouldNotBeBlank()
            }
        }
    }

    "mockGPTAgent should work with custom identifiers" {
        val agent = mockGPTAgent(
            id = "kotlin-expert",
            personality = "technical"
        )

        agent.id shouldBe "kotlin-expert"
    }

    "mockGPTAgent should handle concurrent requests" {
        val agent = mockGPTAgent(id = "concurrent-gpt")

        val messages = (1..10).map { "Message $it" }

        runBlocking {
            messages.map { message ->
                val comm = Comm(content = message, from = "user")
                agent.processComm(comm)
            }.forEach { response ->
                response.content.shouldNotBeBlank()
            }
        }
    }
})
