package io.github.noailabs.spice.graph.checkpoint

    import io.github.noailabs.spice.AgentContext
    import io.github.noailabs.spice.ExecutionContext
    import io.github.noailabs.spice.graph.nodes.GraphExecutionState
    import io.github.noailabs.spice.graph.nodes.HumanInteraction
    import io.github.noailabs.spice.graph.nodes.HumanOption
    import io.github.noailabs.spice.graph.nodes.HumanResponse
    import io.github.noailabs.spice.serialization.SpiceSerializer
    import io.github.noailabs.spice.toAgentContext
    import io.github.noailabs.spice.toExecutionContext
    import kotlinx.serialization.json.*
    import java.time.Instant

    /**
     * 🔄 Type-safe Checkpoint Serialization
     *
     * Preserves nested Map/List structures through JSON serialization cycle.
     * Essential for Redis/DB-backed CheckpointStore implementations to avoid type loss.
     *
     * ## The Problem
     * When storing Checkpoint in Redis/DB, default Jackson serialization loses nested types:
     * ```kotlin
     * // Before: state["data"] = mapOf("key" to "value")  // Map<String, String>
     * // After:  state["data"] = LinkedHashMap<String, Any?>()  // Type lost!
     * // Result: state["data"].toString() = "{key=value}" instead of proper JSON!
     * ```
     *
     * ## The Solution
     * CheckpointSerializer uses SpiceSerializer's recursive `toJsonElement()` to preserve
     * all nested structures as proper JSON, then reconstructs them with correct types.
     */
    object CheckpointSerializer {

        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Serialize Checkpoint to JSON String with type preservation.
         *
         * @param checkpoint The checkpoint to serialize
         * @return JSON string representation
         */
        fun serialize(checkpoint: Checkpoint): String {
        val jsonObject = buildJsonObject {
            put("id", checkpoint.id)
            put("runId", checkpoint.runId)
            put("graphId", checkpoint.graphId)
            put("currentNodeId", checkpoint.currentNodeId)

            // 🔥 Preserve nested structures in state
            put("state", checkpoint.state.toJsonObject())

            // 🔥 Preserve nested structures in metadata (filter null values for Map<String, Any>)
            put("metadata", checkpoint.metadata.toJsonObject())

            // Timestamp as ISO-8601 string
            put("timestamp", checkpoint.timestamp.toString())

            // ExecutionState as string
            put("executionState", checkpoint.executionState.name)

            // AgentContext (ExecutionContext) (if present)
            checkpoint.agentContext?.let { ctx ->
                put("agentContext", ctx.toMap().toJsonObject())
            }

            // HumanInteraction (if present)
            checkpoint.pendingInteraction?.let { interaction ->
                putJsonObject("pendingInteraction") {
                    put("nodeId", interaction.nodeId)
                    put("prompt", interaction.prompt)
                    put("pausedAt", interaction.pausedAt)
                    interaction.expiresAt?.let { put("expiresAt", it) }
                    put("allowFreeText", interaction.allowFreeText)

                    if (interaction.options.isNotEmpty()) {
                        putJsonArray("options") {
                            interaction.options.forEach { option ->
                                addJsonObject {
                                    put("id", option.id)
                                    put("label", option.label)
                                    option.description?.let { put("description", it) }
                                }
                            }
                        }
                    }
                }
            }

            // HumanResponse (if present)
            checkpoint.humanResponse?.let { response ->
                putJsonObject("humanResponse") {
                    put("nodeId", response.nodeId)
                    response.selectedOption?.let { put("selectedOption", it) }
                    response.text?.let { put("text", it) }
                    put("timestamp", response.timestamp)
                    if (response.metadata.isNotEmpty()) {
                        put("metadata", response.metadata.toJsonObject())
                    }
                }
            }
        }

        return jsonObject.toString()
    }

    /**
     * Deserialize JSON String to Checkpoint with type preservation.
     *
     * @param jsonString JSON string representation
     * @return Reconstructed Checkpoint with preserved nested structures
     */
    fun deserialize(jsonString: String): Checkpoint {
        val obj = json.parseToJsonElement(jsonString).jsonObject

        return Checkpoint(
            id = obj["id"]!!.jsonPrimitive.content,
            runId = obj["runId"]!!.jsonPrimitive.content,
            graphId = obj["graphId"]!!.jsonPrimitive.content,
            currentNodeId = obj["currentNodeId"]!!.jsonPrimitive.content,

            // 🔥 Reconstruct nested structures from JSON
            state = parseJsonObjectToMap(obj["state"]!!.jsonObject),
            metadata = parseJsonObjectToMap(obj["metadata"]!!.jsonObject).filterNotNullValues(),

            // Timestamp from ISO-8601 string
            timestamp = Instant.parse(obj["timestamp"]!!.jsonPrimitive.content),

            // ExecutionState from string
            executionState = GraphExecutionState.valueOf(
                obj["executionState"]?.jsonPrimitive?.content ?: "RUNNING"
            ),

            // AgentContext (if present) - deserialize as ExecutionContext then convert
            agentContext = obj["agentContext"]?.jsonObject?.let { ctx ->
                val map = parseJsonObjectToMap(ctx).filterNotNullValues()
                ExecutionContext.of(map).toAgentContext()
            },

            // HumanInteraction (if present)
            pendingInteraction = obj["pendingInteraction"]?.jsonObject?.let { interaction ->
                HumanInteraction(
                    nodeId = interaction["nodeId"]!!.jsonPrimitive.content,
                    prompt = interaction["prompt"]!!.jsonPrimitive.content,
                    options = interaction["options"]?.jsonArray?.map { optionJson ->
                        val optObj = optionJson.jsonObject
                        HumanOption(
                            id = optObj["id"]!!.jsonPrimitive.content,
                            label = optObj["label"]!!.jsonPrimitive.content,
                            description = optObj["description"]?.jsonPrimitive?.contentOrNull
                        )
                    } ?: emptyList(),
                    pausedAt = interaction["pausedAt"]!!.jsonPrimitive.content,
                    expiresAt = interaction["expiresAt"]?.jsonPrimitive?.contentOrNull,
                    allowFreeText = interaction["allowFreeText"]?.jsonPrimitive?.boolean ?: false
                )
            },

            // HumanResponse (if present)
            humanResponse = obj["humanResponse"]?.jsonObject?.let { response ->
                HumanResponse(
                    nodeId = response["nodeId"]!!.jsonPrimitive.content,
                    selectedOption = response["selectedOption"]?.jsonPrimitive?.contentOrNull,
                    text = response["text"]?.jsonPrimitive?.contentOrNull,
                    metadata = response["metadata"]?.jsonObject?.let {
                        parseJsonObjectToMap(it)
                    } ?: emptyMap(),
                    timestamp = response["timestamp"]!!.jsonPrimitive.content
                )
            }
        )
    }

    /**
     * Convert Map to JsonObject using SpiceSerializer (preserves nested structures).
     * Handles Map<String, Any?> by converting to JsonElement recursively.
     */
    private fun Map<String, Any?>.toJsonObject(): JsonObject {
        return buildJsonObject {
            this@toJsonObject.forEach { (key, value) ->
                put(key, SpiceSerializer.run { value.toJsonElement() })
            }
        }
    }

    /**
     * Recursively parse JsonObject to Map<String, Any?> with proper type reconstruction.
     *
     * This is the CRITICAL function that prevents type loss during deserialization.
     */
    private fun parseJsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        return obj.mapValues { (_, value) ->
            jsonElementToAny(value)
        }
    }

    /**
     * Recursively convert JsonElement to appropriate Kotlin type.
     *
     * Handles:
     * - JsonObject → Map<String, Any?>
     * - JsonArray → List<Any?>
     * - JsonPrimitive → String/Number/Boolean
     * - JsonNull → null
     */
    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonObject -> parseJsonObjectToMap(element)  // Recursive!
            is JsonArray -> element.map { jsonElementToAny(it) }  // Recursive!
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    // Try to preserve number types
                    else -> {
                        val content = element.content
                        content.toLongOrNull()
                            ?: content.toDoubleOrNull()
                            ?: content.toBooleanStrictOrNull()
                            ?: content
                    }
                }
            }
            is JsonNull -> null
        }
    }
}

/**
 * Extension to safely get content or null from JsonPrimitive.
 */
private val JsonPrimitive.contentOrNull: String?
    get() = if (isString) content else null

/**
 * Filter null values from Map to convert Map<String, Any?> to Map<String, Any>.
 */
private fun Map<String, Any?>.filterNotNullValues(): Map<String, Any> {
    return this.filterValues { it != null } as Map<String, Any>
}
