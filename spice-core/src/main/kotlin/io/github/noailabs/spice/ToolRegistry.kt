package io.github.noailabs.spice

import io.github.noailabs.spice.validation.ToolSchemaValidator
import io.github.noailabs.spice.validation.ValidationError
import io.github.noailabs.spice.validation.ValidationResult
import java.util.concurrent.ConcurrentHashMap

class ToolWrapper @JvmOverloads constructor(
    override val id: String,
    val tool: Tool,
    val namespace: String = "global",
    val source: String = detectSource(tool),
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, Any> = emptyMap(),
    private val strict: Boolean = false,
    private val schemaValidator: ToolSchemaValidator = ToolSchemaValidator()
) : Tool, Identifiable {
    val schema: ToolSchema? = when (tool) {
        is SimpleTool -> tool.schema
        is AgentTool -> ToolSchema(tool.name, tool.description, emptyMap())
        else -> null
    }

    init {
        schema?.let { schemaValidator.validate(it).throwIfInvalid(tool.name) }
    }

    override val name: String get() = tool.name
    override val description: String get() = tool.description

    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ) = tool.execute(params, context)

    fun canExecute(parameters: Map<String, Any>): Boolean = when (tool) {
        is SimpleTool -> tool.canExecute(parameters)
        else -> true
    }

    fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        val schema = schema ?: return ValidationResult.valid()
        val missing = schema.parameters.filter { it.value.required }.keys - parameters.keys
        return if (missing.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(
                ValidationError("parameters", "Missing required parameters: ${missing.joinToString()}")
            )
        }
    }

    fun toToolSpec(strictOverride: Boolean = strict): Map<String, Any> =
        schema?.toOpenAIFunctionSpec(strictOverride) ?: tool.toOpenAIFunctionSpec(strictOverride)

    companion object {
        fun detectSource(tool: Tool): String = if (tool is AgentTool) "agent-tool" else "direct"
    }
}

object ToolRegistry : Registry<ToolWrapper>("ToolRegistry") {
    private val namespaceIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val tagIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val sourceIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val schemaValidator = ToolSchemaValidator()

    @Volatile
    private var enforceStrictSchemas = false

    fun enableStrictMode(enabled: Boolean) {
        enforceStrictSchemas = enabled
    }

    fun register(
        tool: Tool,
        namespace: String = "global",
        tags: Set<String> = emptySet(),
        metadata: Map<String, Any> = emptyMap(),
        source: String = ToolWrapper.detectSource(tool),
        strictSchema: Boolean = false
    ): Tool {
        val wrapper = ToolWrapper(
            id = idFor(namespace, tool.name),
            tool = tool,
            namespace = namespace,
            source = source,
            tags = tags,
            metadata = metadata,
            strict = strictSchema || enforceStrictSchemas,
            schemaValidator = schemaValidator
        )
        register(wrapper)
        return tool
    }

    fun register(wrapper: ToolWrapper): Tool {
        super.register(wrapper, allowReplace = true)
        index(wrapper)
        return wrapper.tool
    }

    fun getTool(name: String, namespace: String = "global"): Tool? =
        get(idFor(namespace, name))?.tool

    fun getByNamespace(namespace: String): List<Tool> =
        ids(namespaceIndex[namespace]).mapNotNull { get(it)?.tool }

    fun hasTool(name: String, namespace: String = "global"): Boolean =
        get(idFor(namespace, name)) != null

    fun ensureRegistered(name: String, namespace: String = "global"): Boolean =
        hasTool(name, namespace)

    fun register(agentTool: AgentTool, namespace: String = "global"): Tool =
        register(agentTool as Tool, namespace = namespace, source = "agent-tool")

    fun getByTag(tag: String): List<Tool> =
        ids(tagIndex[tag]).mapNotNull { get(it)?.tool }

    fun getBySource(source: String): List<Tool> =
        ids(sourceIndex[source]).mapNotNull { get(it)?.tool }

    fun getWrapper(name: String, namespace: String = "global"): ToolWrapper? =
        get(idFor(namespace, name))

    fun getAgentTools(): List<Pair<Tool, Map<String, Any>>> =
        getAll()
            .filter { it.source == "agent-tool" }
            .map { it.tool to it.metadata }

    override fun unregister(id: String): Boolean {
        val wrapper = get(id) ?: return false
        super.unregister(id)
        removeIndexes(wrapper)
        return true
    }

    override fun clear() {
        super.clear()
        namespaceIndex.clear()
        tagIndex.clear()
        sourceIndex.clear()
    }

    private fun index(wrapper: ToolWrapper) {
        namespaceIndex.add(wrapper.namespace, wrapper.id)
        wrapper.tags.forEach { tagIndex.add(it, wrapper.id) }
        sourceIndex.add(wrapper.source, wrapper.id)
    }

    private fun removeIndexes(wrapper: ToolWrapper) {
        namespaceIndex.removeValue(wrapper.namespace, wrapper.id)
        wrapper.tags.forEach { tagIndex.removeValue(it, wrapper.id) }
        sourceIndex.removeValue(wrapper.source, wrapper.id)
    }

    private fun idFor(namespace: String, name: String) = "$namespace::$name"

    private fun ids(set: MutableSet<String>?): List<String> = set?.toList() ?: emptyList()

    private fun ConcurrentHashMap<String, MutableSet<String>>.add(key: String, value: String) {
        compute(key) { _, existing ->
            val set = existing ?: mutableSetOf()
            set.add(value)  // compute() already provides atomicity
            set
        }
    }

    private fun ConcurrentHashMap<String, MutableSet<String>>.removeValue(key: String, value: String) {
        computeIfPresent(key) { _, existing ->
            existing.remove(value)  // compute() already provides atomicity
            if (existing.isEmpty()) null else existing
        }
    }
}
