package io.github.noailabs.spice.psi

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic PSI (Prompt-Structure-Instructions) tests
 */
class PsiBasicTest {

    @Test
    fun `test PSI template basic structure`() {
        val template = PsiTemplate(
            id = "test-template",
            name = "Test Template",
            version = "1.0",
            structure = PsiStructure(
                sections = listOf(
                    PsiSection(
                        id = "intro",
                        name = "Introduction",
                        content = "This is a test",
                        order = 1
                    )
                )
            )
        )

        assertEquals("test-template", template.id)
        assertEquals(1, template.structure.sections.size)
        assertEquals("intro", template.structure.sections[0].id)
    }

    @Test
    fun `test PSI agent with template`() = runBlocking {
        val template = createSimpleTemplate()
        val agent = PsiTestAgent(template)

        val comm = Comm(
            from = "user",
            to = agent.id,
            content = "Test message"
        )

        val result = agent.processComm(comm)

        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { response ->
                assertTrue(response.content.contains("Test message"))
            },
            onFailure = { error ->
                throw AssertionError("Should not fail: ${error.message}")
            }
        )
    }

    @Test
    fun `test PSI variable substitution`() {
        val template = PsiTemplate(
            id = "var-test",
            name = "Variable Test",
            version = "1.0",
            structure = PsiStructure(
                sections = listOf(
                    PsiSection(
                        id = "content",
                        name = "Content",
                        content = "Hello {{name}}!",
                        order = 1
                    )
                )
            )
        )

        val variables = mapOf("name" to "World")
        val rendered = renderTemplate(template, variables)

        assertTrue(rendered.contains("Hello World!"))
    }

    private fun createSimpleTemplate(): PsiTemplate {
        return PsiTemplate(
            id = "simple",
            name = "Simple Template",
            version = "1.0",
            structure = PsiStructure(
                sections = listOf(
                    PsiSection(
                        id = "main",
                        name = "Main",
                        content = "Process: {{input}}",
                        order = 1
                    )
                )
            )
        )
    }

    private fun renderTemplate(template: PsiTemplate, variables: Map<String, String>): String {
        val sections = template.structure.sections.sortedBy { it.order }
        return sections.joinToString("\n") { section ->
            var content = section.content
            variables.forEach { (key, value) ->
                content = content.replace("{{$key}}", value)
            }
            content
        }
    }
}

/**
 * Test agent using PSI template
 */
class PsiTestAgent(
    private val template: PsiTemplate
) : BaseAgent(
    id = "psi-test-agent",
    name = "PSI Test Agent",
    description = "Agent for PSI testing"
) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Simple processing with template context
        val response = "Processed with template ${template.name}: ${comm.content}"

        return SpiceResult.success(
            comm.reply(
                content = response,
                from = id
            )
        )
    }
}

/**
 * Simple PSI data classes for testing
 */
data class PsiTemplate(
    val id: String,
    val name: String,
    val version: String,
    val structure: PsiStructure
)

data class PsiStructure(
    val sections: List<PsiSection>
)

data class PsiSection(
    val id: String,
    val name: String,
    val content: String,
    val order: Int
)
