package io.github.spice

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
    val responsePatterns: Map<MessageType, ResponsePattern>,
    val vocabulary: PersonaVocabulary,
    val behaviorModifiers: Map<String, Any> = emptyMap()
) {
    
    /**
     * Apply persona to message
     */
    fun applyToMessage(originalContent: String, messageType: MessageType): String {
        val pattern = responsePatterns[messageType] ?: responsePatterns[MessageType.TEXT]!!
        val styledContent = applyPersonalityStyle(originalContent, pattern)
        return vocabulary.enrichResponse(styledContent, personalityType, communicationStyle)
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
)

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
            MessageType.TEXT to ResponsePattern(
                prefix = "Good day.",
                suffix = "I hope this information is helpful.",
                formalityLevel = 0.9
            ),
            MessageType.ERROR to ResponsePattern(
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
            MessageType.TEXT to ResponsePattern(
                prefix = "Hey there!",
                suffix = "Hope that helps!",
                formalityLevel = 0.2
            ),
            MessageType.ERROR to ResponsePattern(
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
            MessageType.TEXT to ResponsePattern(
                prefix = "Well, obviously",
                suffix = "Got it?",
                formalityLevel = 0.3
            ),
            MessageType.ERROR to ResponsePattern(
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
            MessageType.TEXT to ResponsePattern(
                prefix = "Here's a brilliant idea! ✨",
                suffix = "Let's approach this creatively! 🎨",
                formalityLevel = 0.4
            ),
            MessageType.ERROR to ResponsePattern(
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
            MessageType.TEXT to ResponsePattern(
                prefix = "Let me share some wisdom:",
                suffix = "What do you think about this perspective?",
                formalityLevel = 0.7,
                verbosity = 1.3
            ),
            MessageType.ERROR to ResponsePattern(
                prefix = "Mistakes are learning opportunities.",
                suffix = "Let's approach this thoughtfully.",
                formalityLevel = 0.8
            )
        ),
        vocabulary = PersonaVocabulary()
    )
}

/**
 * Personalized agent with applied persona
 */
abstract class PersonalizedAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String>,
    protected val persona: AgentPersona
) : Agent {
    
    /**
     * Process message with persona applied
     */
    override suspend fun processMessage(message: Message): Message {
        // Original processing
        val originalResponse = processMessageWithPersonality(message)
        
        // Apply persona
        val personalizedContent = persona.applyToMessage(originalResponse.content, originalResponse.type)
        
        // Add persona information to metadata
        val personalizedMetadata = originalResponse.metadata + mapOf(
            "persona" to persona.name,
            "personalityType" to persona.personalityType.toString(),
            "communicationStyle" to persona.communicationStyle.toString()
        )
        
        return originalResponse.copy(
            content = personalizedContent,
            metadata = personalizedMetadata
        )
    }
    
    /**
     * Actual message processing logic to be implemented by subclasses
     */
    abstract suspend fun processMessageWithPersonality(message: Message): Message
    
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
}

/**
 * Extension function: Apply persona to existing agent
 */
fun Agent.withPersona(persona: AgentPersona): PersonalizedAgent {
    val originalAgent = this
    
    return object : PersonalizedAgent(
        id = "${originalAgent.id}-personalized",
        name = "${originalAgent.name} (${persona.name})",
        description = "${originalAgent.description} with ${persona.personalityType} personality",
        capabilities = originalAgent.capabilities,
        persona = persona
    ) {
        override suspend fun processMessageWithPersonality(message: Message): Message {
            return originalAgent.processMessage(message)
        }
        
        override fun canHandle(message: Message): Boolean = originalAgent.canHandle(message)
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
    private val responsePatterns = mutableMapOf<MessageType, ResponsePattern>()
    private val behaviorModifiers = mutableMapOf<String, Any>()
    
    fun trait(trait: PersonalityTrait) {
        traits.add(trait)
    }
    
    fun responsePattern(messageType: MessageType, pattern: ResponsePattern) {
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
                mapOf(MessageType.TEXT to ResponsePattern())
            },
            vocabulary = PersonaVocabulary(),
            behaviorModifiers = behaviorModifiers
        )
    }
} 