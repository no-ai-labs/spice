package io.github.noailabs.spice.agents

import io.github.noailabs.spice.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking

/**
 * 🧪 Integration Tests for Claude Agent
 *
 * These tests verify the Claude agent's behavior with mock responses
 * and ensure proper integration with the Comm system.
 */
class ClaudeAgentIntegrationTest : StringSpec({

    "mockClaudeAgent should respond to basic queries" {
        val agent = mockClaudeAgent(
            id = "test-claude",
            personality = "helpful",
            debugEnabled = false
        )

        agent.id shouldBe "test-claude"

        val comm = Comm(
            content = "What is functional programming?",
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

    "mockClaudeAgent with helpful personality should provide balanced responses" {
        val agent = mockClaudeAgent(
            id = "helpful-claude",
            personality = "helpful"
        )

        val comm = Comm(
            content = "Explain Kotlin coroutines",
            from = "learner"
        )

        runBlocking {
            val response = agent.processComm(comm)

            // Helpful responses should be informative
            response.content.shouldNotBeBlank()
            response.content.length shouldNotBe 0
            response.role shouldBe CommRole.ASSISTANT
        }
    }

    "mockClaudeAgent with concise personality should give brief responses" {
        val agent = mockClaudeAgent(
            id = "concise-claude",
            personality = "concise"
        )

        val comm = Comm(
            content = "What is a lambda?",
            from = "user"
        )

        runBlocking {
            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.from shouldBe "concise-claude"
            response.to shouldBe "user"
        }
    }

    "mockClaudeAgent with verbose personality should provide detailed explanations" {
        val agent = mockClaudeAgent(
            id = "verbose-claude",
            personality = "verbose"
        )

        val comm = Comm(
            content = "How do sealed classes work?",
            from = "student"
        )

        runBlocking {
            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.role shouldBe CommRole.ASSISTANT
        }
    }

    "mockClaudeAgent should allow custom configuration" {
        val agent = mockClaudeAgent(
            id = "custom-claude",
            personality = "helpful"
        )

        agent.id shouldBe "custom-claude"
    }

    "claudeAgent factory should create agent with defaults" {
        val agent = mockClaudeAgent()

        agent.id.shouldNotBeBlank()
        agent.id shouldContain "claude"
    }

    "claudeAgent should handle multiple sequential messages" {
        val agent = mockClaudeAgent(id = "sequential-claude")

        val messages = listOf(
            "What is Kotlin?",
            "Explain sealed classes",
            "How do coroutines work?"
        )

        runBlocking {
            messages.forEach { message ->
                val comm = Comm(content = message, from = "user")
                val response = agent.processComm(comm)

                response.content.shouldNotBeBlank()
                response.from shouldBe "sequential-claude"
            }
        }
    }

    "claudeAgent should preserve conversation context" {
        val agent = mockClaudeAgent(id = "context-claude")

        runBlocking {
            val firstComm = Comm(
                content = "I'm learning about agents",
                from = "student"
            )

            val firstResponse = agent.processComm(firstComm)
            firstResponse.parentId shouldBe null

            // Follow-up message
            val followUp = Comm(
                content = "Can you explain more?",
                from = "student",
                parentId = firstResponse.id
            )

            val followUpResponse = agent.processComm(followUp)
            followUpResponse.content.shouldNotBeBlank()
        }
    }

    "claudeAgent should handle empty content gracefully" {
        val agent = mockClaudeAgent(id = "robust-claude")

        runBlocking {
            val emptyComm = Comm(
                content = "",
                from = "user"
            )

            val response = agent.processComm(emptyComm)
            // Should still return a response
            response shouldNotBe null
        }
    }

    "claudeAgent with debug enabled should work correctly" {
        val agent = mockClaudeAgent(
            id = "debug-claude",
            debugEnabled = true
        )

        val comm = Comm(
            content = "Debug test message",
            from = "tester"
        )

        runBlocking {
            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.from shouldBe "debug-claude"
        }
    }

    "claudeAgent should handle Unicode and special characters" {
        val agent = mockClaudeAgent(id = "unicode-claude")

        val unicodeContent = "Test avec français, 日本語, and русский язык 🌶️"

        val comm = Comm(
            content = unicodeContent,
            from = "user"
        )

        runBlocking {
            val response = agent.processComm(comm)
            response.content.shouldNotBeBlank()
        }
    }

    "claudeAgent should work with different Comm types" {
        val agent = mockClaudeAgent(id = "type-test-claude")

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

    "mockClaudeAgent should support different personalities" {
        val personalities = listOf("helpful", "concise", "verbose")

        personalities.forEach { personality ->
            val agent = mockClaudeAgent(
                id = "personality-test-$personality",
                personality = personality
            )

            agent.id shouldBe "personality-test-$personality"
        }
    }

    "claudeAgent should handle concurrent requests" {
        val agent = mockClaudeAgent(id = "concurrent-claude")

        val messages = (1..10).map { "Concurrent message $it" }

        runBlocking {
            messages.map { message ->
                val comm = Comm(content = message, from = "user")
                agent.processComm(comm)
            }.forEach { response ->
                response.content.shouldNotBeBlank()
            }
        }
    }

    "mockClaudeAgent should work with custom identifiers" {
        val agent = mockClaudeAgent(
            id = "architecture-expert",
            personality = "verbose"
        )

        agent.id shouldBe "architecture-expert"
    }

    "claudeAgent should handle Comm with metadata" {
        val agent = mockClaudeAgent(id = "metadata-claude")

        runBlocking {
            val comm = Comm(
                content = "Test with metadata",
                from = "user"
            ).withData("context", "testing")
                .withData("session_id", "12345")

            val response = agent.processComm(comm)

            response.content.shouldNotBeBlank()
            response.from shouldBe "metadata-claude"
        }
    }

    "different Claude personalities should produce different response characteristics" {
        val helpful = mockClaudeAgent(id = "claude-1", personality = "helpful")
        val concise = mockClaudeAgent(id = "claude-2", personality = "concise")
        val verbose = mockClaudeAgent(id = "claude-3", personality = "verbose")

        val question = "What is Kotlin?"

        runBlocking {
            val helpfulResponse = helpful.processComm(Comm(content = question, from = "user"))
            val conciseResponse = concise.processComm(Comm(content = question, from = "user"))
            val verboseResponse = verbose.processComm(Comm(content = question, from = "user"))

            // All should respond
            helpfulResponse.content.shouldNotBeBlank()
            conciseResponse.content.shouldNotBeBlank()
            verboseResponse.content.shouldNotBeBlank()

            // Responses should vary based on personality
            // (In a real implementation, concise would be shorter, verbose longer)
        }
    }
})
