package io.github.noailabs.spice.tools

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.hitl.result.HitlEventEmitter
import io.github.noailabs.spice.hitl.result.NoOpHitlEventEmitter
import io.github.noailabs.spice.hitl.template.HitlOption
import io.github.noailabs.spice.hitl.template.HitlTemplate
import io.github.noailabs.spice.hitl.template.HitlTemplateEngine
import io.github.noailabs.spice.hitl.template.HitlTemplateKind
import io.github.noailabs.spice.hitl.template.HitlTemplateRegistry
import io.github.noailabs.spice.hitl.template.QuantityConfig
import io.github.noailabs.spice.toolspec.OAIToolCall

/**
 * HITL Request Template Tool
 *
 * A tool that uses HitlTemplate to request user input with templated prompts.
 * Supports all HitlTemplateKind types with proper payload generation.
 *
 * **Usage with inline template:**
 * ```kotlin
 * val tool = HitlRequestTemplateTool(
 *     nodeId = "confirm-order",
 *     template = HitlTemplate.confirm(
 *         id = "order-confirm",
 *         prompt = "주문 {{itemCount}}개를 확정하시겠습니까?"
 *     )
 * )
 * ```
 *
 * **Usage with registry template:**
 * ```kotlin
 * val tool = HitlRequestTemplateTool.fromRegistry(
 *     nodeId = "confirm-order",
 *     templateId = "order-confirm",
 *     registry = myRegistry
 * )
 * ```
 *
 * **In Graph DSL:**
 * ```kotlin
 * graph("workflow") {
 *     hitlTemplate("confirm", "order-confirm") {
 *         context { msg -> mapOf("itemCount" to msg.getData<Int>("items")?.size) }
 *     }
 *
 *     decision("route") {
 *         "proceed".whenHitl("yes")
 *         "cancel".whenHitl("no")
 *     }
 * }
 * ```
 *
 * @property nodeId Unique node identifier
 * @property template The HITL template to use
 * @property contextExtractor Function to extract template context from SpiceMessage
 * @property emitter Optional HITL event emitter
 * @property engine Template engine for rendering
 *
 * @since Spice 1.3.5
 */
class HitlRequestTemplateTool(
    val nodeId: String,
    val template: HitlTemplate,
    private val contextExtractor: (ToolContext) -> Map<String, Any> = { emptyMap() },
    private val emitter: HitlEventEmitter = NoOpHitlEventEmitter,
    private val engine: HitlTemplateEngine = HitlTemplateEngine.default
) : Tool {

    override val name: String = "hitl_template_${template.id}"
    override val description: String = "HITL Template: ${template.id} (${template.kind})"

    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val runId = context.graph.runId ?: return SpiceResult.success(
            ToolResult.error(
                error = "runId is required for HITL tools",
                errorCode = "HITL_MISSING_RUNID"
            )
        )

        val graphId = context.graph.graphId

        // Generate stable tool_call_id
        // invocationIndex is passed via params if needed
        val invocationIndex = (params["invocation_index"] as? Number)?.toInt() ?: 0
        val toolCallId = OAIToolCall.generateHitlToolCallId(runId, nodeId) +
            (if (invocationIndex > 0) "_$invocationIndex" else "")

        // Extract context for template
        val templateContext = contextExtractor(context)

        // Bind template with context
        val hitlMetadata = template.bind(
            context = templateContext,
            toolCallId = toolCallId,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            engine = engine
        )

        // Emit HITL request
        emitter.emitHitlRequest(hitlMetadata)

        // Build extended metadata including template info, options metadata, and flags
        val extendedMetadata = buildExtendedMetadata()

        // Create tool call based on template kind
        val toolCall = when (template.kind) {
            HitlTemplateKind.TEXT -> createTextToolCall(
                toolCallId, hitlMetadata.prompt, runId, graphId, extendedMetadata
            )

            HitlTemplateKind.SINGLE_SELECT, HitlTemplateKind.CONFIRM -> createSelectionToolCall(
                toolCallId, hitlMetadata.prompt, runId, graphId, "single", extendedMetadata
            )

            HitlTemplateKind.MULTI_SELECT -> createSelectionToolCall(
                toolCallId, hitlMetadata.prompt, runId, graphId, "multiple", extendedMetadata
            )

            HitlTemplateKind.QUANTITY -> createQuantityToolCall(
                toolCallId, hitlMetadata.prompt, runId, graphId, "single", extendedMetadata
            )

            HitlTemplateKind.MULTI_QUANTITY -> createQuantityToolCall(
                toolCallId, hitlMetadata.prompt, runId, graphId, "multiple", extendedMetadata
            )

            HitlTemplateKind.INFO -> createInfoToolCall(
                toolCallId, hitlMetadata.prompt, runId, graphId, extendedMetadata
            )

            HitlTemplateKind.ESCALATE -> createEscalateToolCall(
                toolCallId, hitlMetadata.prompt, runId, graphId, extendedMetadata
            )
        }

        return SpiceResult.success(
            ToolResult.waitingHitl(
                toolCall = toolCall,
                message = hitlMetadata.prompt,
                metadata = mapOf(
                    "template_id" to template.id,
                    "template_kind" to template.kind.name,
                    "tool_call_id" to toolCallId
                )
            )
        )
    }

    /**
     * Build extended metadata including options with full details and flags
     */
    private fun buildExtendedMetadata(): Map<String, Any> {
        val meta = mutableMapOf<String, Any>()

        // Include template base metadata
        meta.putAll(template.metadata)

        // Include flags
        meta["flags"] = mapOf(
            "required" to template.flags.required,
            "autoProceed" to template.flags.autoProceed,
            "allowSkip" to template.flags.allowSkip,
            "retryCount" to template.flags.retryCount
        ).let { flags ->
            template.flags.timeout?.let { flags + ("timeout" to it) } ?: flags
        }

        // Include quantityConfig if present
        template.quantityConfig?.let { config ->
            meta["quantityConfig"] = mapOf(
                "min" to config.min,
                "max" to config.max,
                "default" to config.defaultValue,
                "step" to config.step
            )
        }

        return meta
    }

    /**
     * Convert HitlOption to map with full metadata including icon
     */
    private fun optionToMap(option: HitlOption): Map<String, Any?> = buildMap {
        put("id", option.id)
        put("label", option.label)
        option.description?.let { put("description", it) }
        option.icon?.let { put("icon", it) }
        if (option.metadata.isNotEmpty()) {
            put("metadata", option.metadata)
        }
    }

    private fun createTextToolCall(
        toolCallId: String,
        prompt: String,
        runId: String,
        graphId: String?,
        metadata: Map<String, Any>
    ): OAIToolCall = OAIToolCall.hitlInput(
        toolCallId = toolCallId,
        prompt = prompt,
        runId = runId,
        nodeId = nodeId,
        graphId = graphId,
        validationRules = template.validationRules.associate { it.type to it.value },
        timeout = template.flags.timeout,
        metadata = metadata
    )

    private fun createSelectionToolCall(
        toolCallId: String,
        prompt: String,
        runId: String,
        graphId: String?,
        selectionType: String,
        metadata: Map<String, Any>
    ): OAIToolCall {
        val options = resolveOptions().map { optionToMap(it) }

        return OAIToolCall.hitlSelection(
            toolCallId = toolCallId,
            prompt = prompt,
            options = options,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            selectionType = selectionType,
            timeout = template.flags.timeout,
            metadata = metadata
        )
    }

    private fun createQuantityToolCall(
        toolCallId: String,
        prompt: String,
        runId: String,
        graphId: String?,
        quantityType: String,
        metadata: Map<String, Any>
    ): OAIToolCall {
        val config = template.quantityConfig ?: QuantityConfig.DEFAULT

        // For multi-quantity, options represent items to select quantities for
        val items = if (quantityType == "multiple") {
            template.options?.map { optionToMap(it) }
        } else {
            null
        }

        return OAIToolCall.hitlQuantity(
            toolCallId = toolCallId,
            prompt = prompt,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            quantityType = quantityType,
            min = config.min,
            max = config.max,
            defaultValue = config.defaultValue,
            step = config.step,
            items = items,
            timeout = template.flags.timeout,
            metadata = metadata
        )
    }

    private fun createInfoToolCall(
        toolCallId: String,
        prompt: String,
        runId: String,
        graphId: String?,
        metadata: Map<String, Any>
    ): OAIToolCall = OAIToolCall.hitlInfo(
        toolCallId = toolCallId,
        prompt = prompt,
        runId = runId,
        nodeId = nodeId,
        graphId = graphId,
        autoProceed = template.flags.autoProceed,
        timeout = template.flags.timeout,
        metadata = metadata
    )

    private fun createEscalateToolCall(
        toolCallId: String,
        prompt: String,
        runId: String,
        graphId: String?,
        metadata: Map<String, Any>
    ): OAIToolCall {
        val priority = template.metadata["priority"] ?: "normal"
        val reason = template.metadata["reason"]

        return OAIToolCall.hitlEscalate(
            toolCallId = toolCallId,
            prompt = prompt,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            reason = reason as? String,
            priority = priority as? String ?: "normal",
            timeout = template.flags.timeout,
            metadata = metadata
        )
    }

    /**
     * Resolve options, providing defaults for CONFIRM type
     */
    private fun resolveOptions(): List<HitlOption> {
        return when {
            !template.options.isNullOrEmpty() -> template.options
            template.kind == HitlTemplateKind.CONFIRM -> HitlOption.yesNo()
            else -> emptyList()
        }
    }

    companion object {
        /**
         * Create a tool from a registry template
         */
        fun fromRegistry(
            nodeId: String,
            templateId: String,
            registry: HitlTemplateRegistry = HitlTemplateRegistry.global,
            tenantId: String? = null,
            contextExtractor: (ToolContext) -> Map<String, Any> = { emptyMap() },
            emitter: HitlEventEmitter = NoOpHitlEventEmitter
        ): HitlRequestTemplateTool {
            val template = registry.resolveOrThrow(templateId, tenantId)
            return HitlRequestTemplateTool(
                nodeId = nodeId,
                template = template,
                contextExtractor = contextExtractor,
                emitter = emitter
            )
        }

        /**
         * Create a simple text input tool
         */
        fun textInput(
            nodeId: String,
            prompt: String,
            emitter: HitlEventEmitter = NoOpHitlEventEmitter
        ) = HitlRequestTemplateTool(
            nodeId = nodeId,
            template = HitlTemplate.text(nodeId, prompt),
            emitter = emitter
        )

        /**
         * Create a confirmation tool
         */
        fun confirm(
            nodeId: String,
            prompt: String,
            yesLabel: String = "Yes",
            noLabel: String = "No",
            emitter: HitlEventEmitter = NoOpHitlEventEmitter
        ) = HitlRequestTemplateTool(
            nodeId = nodeId,
            template = HitlTemplate.confirm(
                id = nodeId,
                prompt = prompt,
                options = HitlOption.yesNo(
                    yesLabel = yesLabel,
                    noLabel = noLabel
                )
            ),
            emitter = emitter
        )

        /**
         * Create a quantity selection tool
         */
        fun quantity(
            nodeId: String,
            prompt: String,
            config: QuantityConfig = QuantityConfig.DEFAULT,
            emitter: HitlEventEmitter = NoOpHitlEventEmitter
        ) = HitlRequestTemplateTool(
            nodeId = nodeId,
            template = HitlTemplate.quantity(nodeId, prompt, config),
            emitter = emitter
        )
    }
}
