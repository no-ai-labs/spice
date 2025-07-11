package io.github.spice

/**
 * 🎭 Spice Agent Persona System
 * 
 * Agent에 persona를 부여해서 스타일/행동을 다르게 만드는 시스템
 */

/**
 * Agent 성격 타입
 */
enum class PersonalityType {
    PROFESSIONAL,   // 전문적이고 공식적
    FRIENDLY,       // 친근하고 따뜻함
    SARCASTIC,      // 비꼬거나 유머러스
    CONCISE,        // 간결하고 직설적
    VERBOSE,        // 상세하고 설명적
    CREATIVE,       // 창의적이고 혁신적
    ANALYTICAL,     // 분석적이고 논리적
    EMPATHETIC,     // 공감적이고 이해심 많음
    ASSERTIVE,      // 단언적이고 확실함
    HUMBLE,         // 겸손하고 조심스러움
    PLAYFUL,        // 장난스럽고 재미있음
    WISE,           // 현명하고 사려깊음
    ENERGETIC,      // 활기차고 열정적
    CALM,           // 차분하고 평온함
    QUIRKY          // 독특하고 개성있음
}

/**
 * 의사소통 스타일
 */
enum class CommunicationStyle {
    FORMAL,         // 격식 있는 말투
    CASUAL,         // 편안한 말투
    TECHNICAL,      // 기술적/전문용어 사용
    SIMPLE,         // 단순하고 쉬운 표현
    POETIC,         // 시적이고 감성적
    HUMOROUS,       // 유머러스하고 재미있음
    DIRECT,         // 직접적이고 명확함
    DIPLOMATIC,     // 외교적이고 완곡함
    ENTHUSIASTIC,   // 열정적이고 에너지 넘침
    CONTEMPLATIVE   // 사색적이고 깊이 있음
}

/**
 * Agent 개성 정의
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
     * 메시지에 persona 적용
     */
    fun applyToMessage(originalContent: String, messageType: MessageType): String {
        val pattern = responsePatterns[messageType] ?: responsePatterns[MessageType.TEXT]!!
        val styledContent = applyPersonalityStyle(originalContent, pattern)
        return vocabulary.enrichResponse(styledContent, personalityType, communicationStyle)
    }
    
    /**
     * 성격 스타일 적용
     */
    private fun applyPersonalityStyle(content: String, pattern: ResponsePattern): String {
        var styledContent = content
        
        // 접두사/접미사 추가
        if (pattern.prefix.isNotBlank()) {
            styledContent = "${pattern.prefix} $styledContent"
        }
        if (pattern.suffix.isNotBlank()) {
            styledContent = "$styledContent ${pattern.suffix}"
        }
        
        // 성격 특성 적용
        traits.forEach { trait ->
            styledContent = trait.transform(styledContent)
        }
        
        return styledContent.trim()
    }
    
    /**
     * 행동 수정자 가져오기
     */
    fun getBehaviorModifier(key: String): Any? = behaviorModifiers[key]
    
    /**
     * confidence 수정
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
 * 성격 특성 인터페이스
 */
interface PersonalityTrait {
    val name: String
    val intensity: Double // 0.0 ~ 1.0
    
    /**
     * 메시지 내용 변환
     */
    fun transform(content: String): String
}

/**
 * 유머러스 특성
 */
class HumorousTrait(override val intensity: Double = 0.7) : PersonalityTrait {
    override val name = "humorous"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        val humorousWords = listOf("ㅋㅋㅋ", "하하", "재밌네요!", "웃겨요", "😄", "🎉")
        val randomHumor = humorousWords.random()
        
        return when {
            intensity > 0.8 -> "$content $randomHumor"
            intensity > 0.5 -> if (Math.random() > 0.5) "$content $randomHumor" else content
            else -> content
        }
    }
}

/**
 * 정중함 특성
 */
class PoliteTrait(override val intensity: Double = 0.8) : PersonalityTrait {
    override val name = "polite"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        val politePrefix = listOf("죄송하지만", "양해 부탁드리며", "정중히 말씀드리면")
        val politeSuffix = listOf("입니다", "니다", "해요", "어요")
        
        var transformed = content
        
        if (intensity > 0.7 && !content.startsWith("죄송") && Math.random() > 0.7) {
            transformed = "${politePrefix.random()} $transformed"
        }
        
        if (intensity > 0.5 && !transformed.endsWith("다") && !transformed.endsWith("요")) {
            val suffix = politeSuffix.random()
            transformed = transformed.removeSuffix(".") + suffix + "."
        }
        
        return transformed
    }
}

/**
 * 간결함 특성
 */
class ConciseTrait(override val intensity: Double = 0.8) : PersonalityTrait {
    override val name = "concise"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        var transformed = content
        
        // 불필요한 단어 제거
        val unnecessaryWords = listOf("그런데", "그러므로", "또한", "더불어", "게다가")
        unnecessaryWords.forEach { word ->
            if (intensity > 0.6) {
                transformed = transformed.replace(word, "")
            }
        }
        
        // 문장 길이 제한
        if (intensity > 0.8 && transformed.length > 100) {
            val sentences = transformed.split(".")
            transformed = sentences.take((sentences.size * 0.7).toInt()).joinToString(".")
            if (!transformed.endsWith(".")) transformed += "."
        }
        
        return transformed.replace("  ", " ").trim()
    }
}

/**
 * 창의적 특성
 */
class CreativeTrait(override val intensity: Double = 0.6) : PersonalityTrait {
    override val name = "creative"
    
    override fun transform(content: String): String {
        if (intensity < 0.3) return content
        
        val creativeWords = mapOf(
            "좋은" to listOf("멋진", "훌륭한", "환상적인", "뛰어난"),
            "만들다" to listOf("창조하다", "구성하다", "디자인하다", "혁신하다"),
            "생각하다" to listOf("상상하다", "구상하다", "발상하다", "창안하다")
        )
        
        var transformed = content
        
        if (intensity > 0.5) {
            creativeWords.forEach { (original, alternatives) ->
                if (transformed.contains(original) && Math.random() > 0.6) {
                    transformed = transformed.replace(original, alternatives.random())
                }
            }
        }
        
        // 창의적 이모지 추가
        if (intensity > 0.8 && Math.random() > 0.7) {
            val emojis = listOf("✨", "🎨", "💡", "🚀", "⭐")
            transformed += " ${emojis.random()}"
        }
        
        return transformed
    }
}

/**
 * 응답 패턴
 */
data class ResponsePattern(
    val prefix: String = "",
    val suffix: String = "",
    val emotionalTone: String = "neutral",
    val verbosity: Double = 1.0, // 1.0 = 보통, >1.0 = 더 자세히, <1.0 = 더 간결히
    val formalityLevel: Double = 0.5 // 0.0 = 매우 캐주얼, 1.0 = 매우 격식적
)

/**
 * Persona 어휘집
 */
class PersonaVocabulary {
    private val vocabularyMap = mapOf(
        PersonalityType.PROFESSIONAL to mapOf(
            "greeting" to listOf("안녕하세요", "반갑습니다", "좋은 하루입니다"),
            "agreement" to listOf("그렇습니다", "동의합니다", "맞습니다"),
            "closing" to listOf("감사합니다", "좋은 하루 되세요", "도움이 되었기를 바랍니다")
        ),
        PersonalityType.FRIENDLY to mapOf(
            "greeting" to listOf("안녕!", "하이!", "반가워요!"),
            "agreement" to listOf("맞아요!", "그래요!", "완전 동감!"),
            "closing" to listOf("고마워요!", "좋은 하루!", "또 만나요!")
        ),
        PersonalityType.SARCASTIC to mapOf(
            "greeting" to listOf("아, 안녕하세요", "뭐 어쩌라고요", "그러게요"),
            "agreement" to listOf("당연하죠", "뻔한 얘기네요", "그럴 줄 알았어요"),
            "closing" to listOf("그럼 그렇게 하세요", "뭐 좋으시겠네요", "알아서 하세요")
        )
    )
    
    fun enrichResponse(content: String, personality: PersonalityType, style: CommunicationStyle): String {
        val vocabulary = vocabularyMap[personality] ?: return content
        
        var enriched = content
        
        // 격식에 따른 문체 조정
        when (style) {
            CommunicationStyle.FORMAL -> {
                enriched = enriched.replace("야", "").replace("ㅋㅋ", "")
                if (!enriched.endsWith("다") && !enriched.endsWith("요")) {
                    enriched += "습니다"
                }
            }
            CommunicationStyle.CASUAL -> {
                enriched = enriched.replace("습니다", "어요").replace("입니다", "이에요")
            }
            CommunicationStyle.HUMOROUS -> {
                if (Math.random() > 0.6) {
                    enriched += " ㅋㅋ"
                }
            }
            else -> {}
        }
        
        return enriched
    }
}

/**
 * 사전 정의된 Persona들
 */
object PersonaLibrary {
    
    val PROFESSIONAL_ASSISTANT = AgentPersona(
        name = "Professional Assistant",
        personalityType = PersonalityType.PROFESSIONAL,
        communicationStyle = CommunicationStyle.FORMAL,
        traits = setOf(PoliteTrait(0.9), ConciseTrait(0.6)),
        responsePatterns = mapOf(
            MessageType.TEXT to ResponsePattern(
                prefix = "안녕하세요.",
                suffix = "도움이 되었기를 바랍니다.",
                formalityLevel = 0.9
            ),
            MessageType.ERROR to ResponsePattern(
                prefix = "죄송합니다.",
                suffix = "다시 시도해 주시기 바랍니다.",
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
                prefix = "안녕!",
                suffix = "도움이 되었으면 좋겠어!",
                formalityLevel = 0.2
            ),
            MessageType.ERROR to ResponsePattern(
                prefix = "앗, 미안!",
                suffix = "다시 해보자!",
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
                prefix = "뭐, 그럼",
                suffix = "이해하셨나요?",
                formalityLevel = 0.3
            ),
            MessageType.ERROR to ResponsePattern(
                prefix = "예상했던 일이네요.",
                suffix = "다음엔 더 조심하세요.",
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
                prefix = "아이디어가 떠오르네요! ✨",
                suffix = "창의적으로 접근해보죠! 🎨",
                formalityLevel = 0.4
            ),
            MessageType.ERROR to ResponsePattern(
                prefix = "실패는 성공의 어머니라고 하죠 💡",
                suffix = "새로운 방법을 시도해봅시다! 🚀",
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
                prefix = "생각해보니,",
                suffix = "이런 관점은 어떠신가요?",
                formalityLevel = 0.7,
                verbosity = 1.3
            ),
            MessageType.ERROR to ResponsePattern(
                prefix = "실수는 배움의 기회입니다.",
                suffix = "천천히 다시 접근해보시죠.",
                formalityLevel = 0.8
            )
        ),
        vocabulary = PersonaVocabulary()
    )
}

/**
 * Persona가 적용된 Agent
 */
abstract class PersonalizedAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String>,
    protected val persona: AgentPersona
) : Agent {
    
    /**
     * Persona가 적용된 메시지 처리
     */
    override suspend fun processMessage(message: Message): Message {
        // 원본 처리
        val originalResponse = processMessageWithPersonality(message)
        
        // Persona 적용
        val personalizedContent = persona.applyToMessage(originalResponse.content, originalResponse.type)
        
        // 메타데이터에 persona 정보 추가
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
     * 하위 클래스에서 구현할 실제 메시지 처리 로직
     */
    abstract suspend fun processMessageWithPersonality(message: Message): Message
    
    /**
     * Persona 정보는 persona 프로퍼티로 직접 접근 가능
     */
    
    /**
     * 성격 특성 확인
     */
    fun hasTrait(traitName: String): Boolean = persona.traits.any { it.name == traitName }
    
    /**
     * 행동 수정자 적용된 confidence 계산
     */
    protected fun calculatePersonalizedConfidence(baseConfidence: Double): Double {
        return persona.modifyConfidence(baseConfidence)
    }
}

/**
 * 확장 함수: 기존 Agent에 Persona 적용
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
 * DSL로 커스텀 Persona 생성
 */
fun buildPersona(name: String, init: PersonaBuilder.() -> Unit): AgentPersona {
    val builder = PersonaBuilder(name)
    builder.init()
    return builder.build()
}

/**
 * Persona 빌더
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