package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult

/**
 * 🎭 Spice Agent Persona System
 *
 * System that gives agents personas to make their styles and behaviors different
 */

/**
 * Agent personality types
 */
enum class PersonalityType {
    PROFESSIONAL,   // Professional and formal
    FRIENDLY,       // Friendly and warm
    SARCASTIC,      // Sarcastic or humorous
    CONCISE,        // Concise and direct
    VERBOSE,        // Detailed and descriptive
    CREATIVE,       // Creative and innovative
    ANALYTICAL,     // Analytical and logical
    EMPATHETIC,     // Empathetic and understanding
    ASSERTIVE,      // Assertive and confident
    HUMBLE,         // Humble and cautious
    PLAYFUL,        // Playful and fun
    WISE,           // Wise and thoughtful
    ENERGETIC,      // Energetic and passionate
    CALM,           // Calm and peaceful
    QUIRKY          // Unique and distinctive
}

/**
 * Communication styles
 */
enum class CommunicationStyle {
    FORMAL,         // Formal tone
    CASUAL,         // Casual tone
    TECHNICAL,      // Technical/professional terminology
    SIMPLE,         // Simple and easy expressions
    POETIC,         // Poetic and emotional
    HUMOROUS,       // Humorous and fun
    DIRECT,         // Direct and clear
    DIPLOMATIC,     // Diplomatic and tactful
    ENTHUSIASTIC,   // Enthusiastic and energetic
    CONTEMPLATIVE   // Contemplative and deep
}

/**
 * Agent personality definition
 */
data class AgentPersona(
    val name: String,
    val personalityType: PersonalityType,
    val communicationStyle: CommunicationStyle,
    val traits: Set<PersonalityTrait>,
    val responsePatterns: Map<CommType, ResponsePattern>,
    val vocabulary: PersonaVocabulary,
    val behaviorModifiers: Map<String, Any> = emptyMap()
) {
    
    /**
     * Apply persona to message
     */
    fun applyToResponse(originalResponse: String, messageType: CommType = CommType.TEXT): String {
        var response = originalResponse
        
        // Apply response pattern
        responsePatterns[messageType]?.let { pattern ->
            response = pattern.transform(response)
        }
        
        // Apply vocabulary
        response = vocabulary.apply(response)
        
        // Apply communication style
        response = applyCommunicationStyle(response)
        
        // Apply personality traits
        traits.forEach { trait ->
            response = trait.apply(response)
        }
        
        return response
    }
    
    /**
     * Apply personality style
     */
    private fun applyPersonalityStyle(content: String, pattern: ResponsePattern): String {
        var styledContent = content
        
        // Add prefix/suffix
        if (pattern.prefix.isNotBlank()) {
            styledContent = "${pattern.prefix} $styledContent"
        }
        if (pattern.suffix.isNotBlank()) {
            styledContent = "$styledContent ${pattern.suffix}"
        }
        
        // Apply personality traits
        traits.forEach { trait ->
            styledContent = trait.transform(styledContent)
        }
        
        return styledContent.trim()
    }
    
    /**
     * Get behavior modifier
     */
    fun getBehaviorModifier(key: String): Any? = behaviorModifiers[key]
    
    /**
     * Modify confidence
     */
    fun modifyConfidence(originalConfidence: Double): Double {
        val modifier = when (personalityType) {
            PersonalityType.ASSERTIVE -> 1.1
            PersonalityType.HUMBLE -> 0.9
            PersonalityType.PROFESSIONAL -> 1.05
            else -> 1.0
        }
        return (originalConfidence * modifier).coerceIn(0.0, 1.0)
    }

    private fun applyCommunicationStyle(content: String): String {
        return when (communicationStyle) {
            CommunicationStyle.FORMAL -> makeFormals(content)
            CommunicationStyle.CASUAL -> makeCasual(content)
            CommunicationStyle.TECHNICAL -> content // Keep as-is for technical
            CommunicationStyle.SIMPLE -> simplify(content)
            CommunicationStyle.POETIC -> makePoetic(content)
            CommunicationStyle.HUMOROUS -> makeHumorous(content)
            CommunicationStyle.DIRECT -> makeDirect(content)
            CommunicationStyle.DIPLOMATIC -> makeDiplomatic(content)
            CommunicationStyle.ENTHUSIASTIC -> makeEnthusiastic(content)
            CommunicationStyle.CONTEMPLATIVE -> makeContemplative(content)
        }
    }
    
    private fun makeFormals(content: String): String {
        return content
            .replace("can't", "cannot")
            .replace("won't", "will not")
            .replace("don't", "do not")
    }
    
    private fun makeCasual(content: String): String {
        return content
            .replace("cannot", "can't")
            .replace("will not", "won't")
            .replace("do not", "don't")
    }
    
    private fun simplify(content: String): String {
        // Simple implementation - in real world would be more sophisticated
        return content.replace(Regex("\\b\\w{10,}\\b")) { match ->
            when (match.value.lowercase()) {
                "furthermore" -> "also"
                "additionally" -> "also"
                "consequently" -> "so"
                else -> match.value
            }
        }
    }
    
    private fun makePoetic(content: String): String {
        return content // Would add poetic elements in real implementation
    }
    
    private fun makeHumorous(content: String): String {
        return content // Would add humor in real implementation
    }
    
    private fun makeDirect(content: String): String {
        return content.replace(Regex("(In my opinion,|I think that|I believe)"), "")
    }
    
    private fun makeDiplomatic(content: String): String {
        return "Perhaps we could consider that $content"
    }
    
    private fun makeEnthusiastic(content: String): String {
        return "$content!"
    }
    
    private fun makeContemplative(content: String): String {
        return "When we reflect on this, $content"
    }
}

/**
 * Personality trait interface
 */
interface PersonalityTrait {
    val name: String
    val intensity: Double // 0.0 ~ 1.0
    
    /**
     * Transform message content
     */
    fun transform(content: String): String
    
    /**
     * Apply trait to content (delegates to transform by default)
     */
    fun apply(content: String): String = transform(content)
}

/**
 * Humorous trait
 */
class HumorousTrait(override val intensity: Double = 0.7) : PersonalityTrait {
    override val name = "humorous"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        val humorousWords = listOf("haha", "lol", "that's funny!", "amusing", "😄", "🎉")
        val randomHumor = humorousWords.random()
        
        return when {
            intensity > 0.8 -> "$content $randomHumor"
            intensity > 0.5 -> if (Math.random() > 0.5) "$content $randomHumor" else content
            else -> content
        }
    }
}

/**
 * Polite trait
 */
class PoliteTrait(override val intensity: Double = 0.8) : PersonalityTrait {
    override val name = "polite"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        val politePrefix = listOf("If I may", "With respect", "Kindly note that")
        val politeSuffix = listOf("please", "if you don't mind", "thank you")
        
        var transformed = content
        
        if (intensity > 0.7 && !content.startsWith("If") && Math.random() > 0.7) {
            transformed = "${politePrefix.random()}, $transformed"
        }
        
        if (intensity > 0.5 && Math.random() > 0.6) {
            val suffix = politeSuffix.random()
            transformed = "$transformed, $suffix"
        }
        
        return transformed
    }
}

/**
 * Concise trait
 */
class ConciseTrait(override val intensity: Double = 0.8) : PersonalityTrait {
    override val name = "concise"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        var transformed = content
        
        // Remove unnecessary words
        val unnecessaryWords = listOf("however", "therefore", "moreover", "furthermore", "additionally")
        unnecessaryWords.forEach { word ->
            if (intensity > 0.6) {
                transformed = transformed.replace(word, "")
            }
        }
        
        // Limit sentence length
        if (intensity > 0.8 && transformed.length > 100) {
            val sentences = transformed.split(".")
            transformed = sentences.take((sentences.size * 0.7).toInt()).joinToString(".")
            if (!transformed.endsWith(".")) transformed += "."
        }
        
        return transformed.replace("  ", " ").trim()
    }
}

/**
 * Creative trait
 */
class CreativeTrait(override val intensity: Double = 0.6) : PersonalityTrait {
    override val name = "creative"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        val creativeWords = mapOf(
            "good" to listOf("excellent", "outstanding", "fantastic", "remarkable"),
            "make" to listOf("create", "craft", "design", "innovate"),
            "think" to listOf("imagine", "envision", "conceive", "ideate")
        )
        
        var transformed = content
        
        if (intensity > 0.5) {
            creativeWords.forEach { (original, alternatives) ->
                if (transformed.contains(original, ignoreCase = true) && Math.random() > 0.6) {
                    transformed = transformed.replace(original, alternatives.random(), ignoreCase = true)
                }
            }
        }
        
        // Add creative emojis
        if (intensity > 0.8 && Math.random() > 0.7) {
            val emojis = listOf("✨", "🎨", "💡", "🚀", "⭐")
            transformed += " ${emojis.random()}"
        }
        
        return transformed
    }
}

/**
 * Response pattern
 */
data class ResponsePattern(
    val prefix: String = "",
    val suffix: String = "",
    val emotionalTone: String = "neutral",
    val verbosity: Double = 1.0, // 1.0 = normal, >1.0 = more detailed, <1.0 = more concise
    val formalityLevel: Double = 0.5 // 0.0 = very casual, 1.0 = very formal
) {
    fun transform(content: String): String {
        var styledContent = content
        
        // Add prefix/suffix
        if (prefix.isNotBlank()) {
            styledContent = "$prefix $styledContent"
        }
        if (suffix.isNotBlank()) {
            styledContent = "$styledContent $suffix"
        }
        
        return styledContent.trim()
    }
}

/**
 * Persona vocabulary
 */
class PersonaVocabulary {
    private val vocabularyMap = mapOf(
        PersonalityType.PROFESSIONAL to mapOf(
            "greeting" to listOf("Hello", "Good day", "Greetings"),
            "agreement" to listOf("Certainly", "I agree", "That's correct"),
            "closing" to listOf("Thank you", "Have a great day", "Hope this helps")
        ),
        PersonalityType.FRIENDLY to mapOf(
            "greeting" to listOf("Hi there!", "Hey!", "Hello friend!"),
            "agreement" to listOf("Absolutely!", "You got it!", "Totally agree!"),
            "closing" to listOf("Thanks a bunch!", "Take care!", "See you around!")
        ),
        PersonalityType.SARCASTIC to mapOf(
            "greeting" to listOf("Oh, hello there", "Well, well", "How delightful"),
            "agreement" to listOf("Obviously", "What a surprise", "As expected"),
            "closing" to listOf("There you go", "Hope you're satisfied", "You're welcome, I guess")
        )
    )
    
    fun enrichResponse(content: String, personality: PersonalityType, style: CommunicationStyle): String {
        val vocabulary = vocabularyMap[personality] ?: return content
        
        var enriched = content
        
        // Adjust formality based on style
        when (style) {
            CommunicationStyle.FORMAL -> {
                enriched = enriched.replace("gonna", "going to").replace("wanna", "want to")
                if (!enriched.endsWith(".") && !enriched.endsWith("!") && !enriched.endsWith("?")) {
                    enriched += "."
                }
            }
            CommunicationStyle.CASUAL -> {
                enriched = enriched.replace("going to", "gonna").replace("want to", "wanna")
            }
            CommunicationStyle.HUMOROUS -> {
                if (Math.random() > 0.6) {
                    enriched += " 😄"
                }
            }
            else -> {}
        }
        
        return enriched
    }
    
    // Simple apply method for AgentPersona usage
    fun apply(content: String): String = content
}

/**
 * Pre-defined personas
 */
object PersonaLibrary {
    
    val PROFESSIONAL_ASSISTANT = AgentPersona(
        name = "Professional Assistant",
        personalityType = PersonalityType.PROFESSIONAL,
        communicationStyle = CommunicationStyle.FORMAL,
        traits = setOf(PoliteTrait(0.9), ConciseTrait(0.6)),
        responsePatterns = mapOf(
            CommType.TEXT to ResponsePattern(
                prefix = "Good day.",
                suffix = "I hope this information is helpful.",
                formalityLevel = 0.9
            ),
            CommType.ERROR to ResponsePattern(
                prefix = "I apologize for the inconvenience.",
                suffix = "Please try again.",
                formalityLevel = 1.0
            )
        ),
        vocabulary = PersonaVocabulary()
    )
    
    val FRIENDLY_BUDDY = AgentPersona(
        name = "Friendly Buddy",
        personalityType = PersonalityType.FRIENDLY,
        communicationStyle = CommunicationStyle.CASUAL,
        traits = setOf(HumorousTrait(0.7), PoliteTrait(0.4)),
        responsePatterns = mapOf(
            CommType.TEXT to ResponsePattern(
                prefix = "Hey there!",
                suffix = "Hope that helps!",
                formalityLevel = 0.2
            ),
            CommType.ERROR to ResponsePattern(
                prefix = "Oops, my bad!",
                suffix = "Let's try that again!",
                formalityLevel = 0.1
            )
        ),
        vocabulary = PersonaVocabulary()
    )
    
    val SARCASTIC_EXPERT = AgentPersona(
        name = "Sarcastic Expert",
        personalityType = PersonalityType.SARCASTIC,
        communicationStyle = CommunicationStyle.DIRECT,
        traits = setOf(HumorousTrait(0.9), ConciseTrait(0.8)),
        responsePatterns = mapOf(
            CommType.TEXT to ResponsePattern(
                prefix = "Well, obviously",
                suffix = "Got it?",
                formalityLevel = 0.3
            ),
            CommType.ERROR to ResponsePattern(
                prefix = "Saw that coming.",
                suffix = "Maybe be more careful next time.",
                formalityLevel = 0.4
            )
        ),
        vocabulary = PersonaVocabulary()
    )
    
    val CREATIVE_GENIUS = AgentPersona(
        name = "Creative Genius",
        personalityType = PersonalityType.CREATIVE,
        communicationStyle = CommunicationStyle.POETIC,
        traits = setOf(CreativeTrait(0.9), HumorousTrait(0.5)),
        responsePatterns = mapOf(
            CommType.TEXT to ResponsePattern(
                prefix = "Here's a brilliant idea! ✨",
                suffix = "Let's approach this creatively! 🎨",
                formalityLevel = 0.4
            ),
            CommType.ERROR to ResponsePattern(
                prefix = "Every failure is a stepping stone to success 💡",
                suffix = "Let's try a new approach! 🚀",
                formalityLevel = 0.3
            )
        ),
        vocabulary = PersonaVocabulary(),
        behaviorModifiers = mapOf(
            "confidenceBoost" to 0.15,
            "risktaking" to 0.8
        )
    )
    
    val WISE_MENTOR = AgentPersona(
        name = "Wise Mentor",
        personalityType = PersonalityType.WISE,
        communicationStyle = CommunicationStyle.CONTEMPLATIVE,
        traits = setOf(PoliteTrait(0.8), ConciseTrait(0.3)),
        responsePatterns = mapOf(
            CommType.TEXT to ResponsePattern(
                prefix = "Let me share some wisdom:",
                suffix = "What do you think about this perspective?",
                formalityLevel = 0.7,
                verbosity = 1.3
            ),
            CommType.ERROR to ResponsePattern(
                prefix = "Mistakes are learning opportunities.",
                suffix = "Let's approach this thoughtfully.",
                formalityLevel = 0.8
            )
        ),
        vocabulary = PersonaVocabulary()
    )
}

/**
 * Base agent adapter that applies persona
 */
abstract class PersonaAdapter(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String>,
    protected val persona: AgentPersona
) : Agent {
    
    /**
     * Process comm with persona applied
     */
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Original processing
        val originalResponse = processCommWithPersonality(comm)

        return originalResponse.fold(
            onSuccess = { response ->
                // Apply persona
                val personalizedContent = persona.applyToResponse(response.content, response.type)

                // Add persona information to data
                val personalizedData = response.data + mapOf(
                    "persona" to persona.name,
                    "personalityType" to persona.personalityType.toString(),
                    "communicationStyle" to persona.communicationStyle.toString()
                )

                SpiceResult.success(response.copy(
                    content = personalizedContent,
                    data = personalizedData
                ))
            },
            onFailure = { error -> SpiceResult.failure(error) }
        )
    }

    /**
     * Actual comm processing logic to be implemented by subclasses
     */
    abstract suspend fun processCommWithPersonality(comm: Comm): SpiceResult<Comm>
    
    /**
     * Check if agent has specific trait
     */
    fun hasTrait(traitName: String): Boolean = persona.traits.any { it.name == traitName }
    
    /**
     * Calculate confidence with behavior modifiers applied
     */
    protected fun calculatePersonalizedConfidence(baseConfidence: Double): Double {
        return persona.modifyConfidence(baseConfidence)
    }
    
    override fun canHandle(comm: Comm): Boolean = true
    
    override fun getTools(): List<Tool> = emptyList()
    
    override fun isReady(): Boolean = true
}

/**
 * Extension function: Apply persona to existing agent
 */
fun Agent.withPersona(persona: AgentPersona): PersonaAdapter {
    val originalAgent = this
    
    return object : PersonaAdapter(
        id = "${originalAgent.id}-personalized",
        name = "${originalAgent.name} (${persona.name})",
        description = "${originalAgent.description} with ${persona.personalityType} personality",
        capabilities = originalAgent.capabilities,
        persona = persona
    ) {
        override suspend fun processCommWithPersonality(comm: Comm): SpiceResult<Comm> {
            return originalAgent.processComm(comm)
        }
        
        override fun canHandle(comm: Comm): Boolean = originalAgent.canHandle(comm)
        override fun getTools(): List<Tool> = originalAgent.getTools()
        override fun isReady(): Boolean = originalAgent.isReady()
    }
}

/**
 * DSL for creating custom personas
 */
fun buildPersona(name: String, init: PersonaBuilder.() -> Unit): AgentPersona {
    val builder = PersonaBuilder(name)
    builder.init()
    return builder.build()
}

/**
 * Persona builder
 */
class PersonaBuilder(private val name: String) {
    var personalityType: PersonalityType = PersonalityType.FRIENDLY
    var communicationStyle: CommunicationStyle = CommunicationStyle.CASUAL
    private val traits = mutableSetOf<PersonalityTrait>()
    private val responsePatterns = mutableMapOf<CommType, ResponsePattern>()
    private val behaviorModifiers = mutableMapOf<String, Any>()
    
    fun trait(trait: PersonalityTrait) {
        traits.add(trait)
    }
    
    fun responsePattern(messageType: CommType, pattern: ResponsePattern) {
        responsePatterns[messageType] = pattern
    }
    
    fun behaviorModifier(key: String, value: Any) {
        behaviorModifiers[key] = value
    }
    
    internal fun build(): AgentPersona {
        return AgentPersona(
            name = name,
            personalityType = personalityType,
            communicationStyle = communicationStyle,
            traits = traits,
            responsePatterns = responsePatterns.ifEmpty {
                mapOf(CommType.TEXT to ResponsePattern())
            },
            vocabulary = PersonaVocabulary(),
            behaviorModifiers = behaviorModifiers
        )
    }
} 