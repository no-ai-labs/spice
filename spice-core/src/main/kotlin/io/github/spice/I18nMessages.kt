package io.github.spice

import java.util.Locale

/**
 * 🌍 I18nMessages - Internationalization support for Spice Framework
 * 
 * Provides bilingual (English/Korean) message support for better user experience.
 * Messages are automatically selected based on locale or can be explicitly requested.
 */
object I18nMessages {
    
    private val messages = mapOf(
        // Error Messages
        "error.processing" to mapOf(
            "en" to "Sorry, an error occurred during processing: %s",
            "ko" to "죄송합니다. 처리 중 오류가 발생했습니다: %s"
        ),
        "error.system" to mapOf(
            "en" to "Sorry, a system error occurred: %s",
            "ko" to "죄송합니다. 시스템 오류가 발생했습니다: %s"
        ),
        "error.connection" to mapOf(
            "en" to "Sorry, a connection error occurred: %s",
            "ko" to "죄송합니다. 연결 오류가 발생했습니다: %s"
        ),
        "error.generation" to mapOf(
            "en" to "Sorry, an error occurred while generating response: %s",
            "ko" to "죄송합니다. 응답을 생성하는 중 오류가 발생했습니다: %s"
        ),
        "error.planning" to mapOf(
            "en" to "Sorry, an error occurred during plan generation: %s",
            "ko" to "죄송합니다. 계획 생성 중 오류가 발생했습니다: %s"
        ),
        "error.planning.system" to mapOf(
            "en" to "Sorry, a planning system error occurred: %s",
            "ko" to "죄송합니다. 계획 생성 시스템 오류가 발생했습니다: %s"
        ),
        "error.claude.generation" to mapOf(
            "en" to "Sorry, Claude encountered an error while generating response: %s",
            "ko" to "죄송합니다. Claude가 응답을 생성하는 중 오류가 발생했습니다: %s"
        ),
        "error.vllm.processing" to mapOf(
            "en" to "Sorry, vLLM server encountered an error during processing: %s",
            "ko" to "죄송합니다. vLLM 서버 처리 중 오류가 발생했습니다: %s"
        ),
        "error.vllm.connection" to mapOf(
            "en" to "Sorry, vLLM server connection error occurred: %s",
            "ko" to "죄송합니다. vLLM 서버 연결 오류가 발생했습니다: %s"
        ),
        "error.openrouter.processing" to mapOf(
            "en" to "Sorry, OpenRouter encountered an error during processing: %s",
            "ko" to "죄송합니다. OpenRouter 처리 중 오류가 발생했습니다: %s"
        ),
        "error.openrouter.connection" to mapOf(
            "en" to "Sorry, OpenRouter connection error occurred: %s",
            "ko" to "죄송합니다. OpenRouter 연결 오류가 발생했습니다: %s"
        ),
        
        // Session Messages
        "session.coding.started" to mapOf(
            "en" to "🚀 vLLM coding session has started! Please ask questions about %s.",
            "ko" to "🚀 vLLM 코딩 세션이 시작되었습니다! %s 관련 질문을 해주세요."
        ),
        "session.learning.started" to mapOf(
            "en" to "🌐 OpenRouter learning session has started! What would you like to learn about %s?",
            "ko" to "🌐 OpenRouter 학습 세션이 시작되었습니다! %s 에 대해 무엇을 배우고 싶으신가요?"
        ),
        
        // Status Messages
        "status.model" to mapOf(
            "en" to "Model: %s",
            "ko" to "모델: %s"
        ),
        "status.context" to mapOf(
            "en" to "Context: %d messages, %d tokens",
            "ko" to "컨텍스트: %d개 메시지, %d개 토큰"
        ),
        "status.system_prompt_active" to mapOf(
            "en" to "System Prompt: Active",
            "ko" to "시스템 프롬프트: 활성화"
        ),
        "status.server" to mapOf(
            "en" to "Server: %s",
            "ko" to "서버: %s"
        ),
        "status.online" to mapOf(
            "en" to "ONLINE",
            "ko" to "온라인"
        ),
        "status.offline" to mapOf(
            "en" to "OFFLINE",
            "ko" to "오프라인"
        ),
        
        // Planning Messages
        "planning.goal_analysis" to mapOf(
            "en" to "🎯 GOAL ANALYSIS AND PLANNING",
            "ko" to "🎯 목표 분석 및 계획 수립"
        ),
        "planning.analyze_goal" to mapOf(
            "en" to "Please analyze the following goal and create a detailed, actionable plan:",
            "ko" to "다음 목표를 분석하고 상세하고 실행 가능한 계획을 수립해주세요:"
        ),
        "planning.goal" to mapOf(
            "en" to "Goal: %s",
            "ko" to "목표: %s"
        ),
        "planning.domain" to mapOf(
            "en" to "Domain: %s",
            "ko" to "영역: %s"
        ),
        "planning.priority" to mapOf(
            "en" to "Priority: %s",
            "ko" to "우선순위: %s"
        ),
        "planning.constraints" to mapOf(
            "en" to "Constraints:",
            "ko" to "제약사항:"
        ),
        "planning.estimated_time" to mapOf(
            "en" to "⏱️ Estimated time: %d minutes",
            "ko" to "⏱️ 예상 시간: %d분"
        ),
        "planning.dependencies" to mapOf(
            "en" to "🔗 Dependencies: %s",
            "ko" to "🔗 의존성: %s"
        ),
        
        // Cost Messages
        "cost.estimated" to mapOf(
            "en" to "Estimated cost: $%s",
            "ko" to "예상 비용: $%s"
        ),
        "cost.calculation_failed" to mapOf(
            "en" to "Cost calculation unavailable",
            "ko" to "비용 계산 불가"
        ),
        "cost.pricing_info_unavailable" to mapOf(
            "en" to "Pricing information unavailable",
            "ko" to "가격 정보 없음"
        ),
        
        // Common phrases and greetings
        "hello" to mapOf(
            "en" to "Hello",
            "ko" to "안녕하세요"
        ),
        "hi" to mapOf(
            "en" to "Hi",
            "ko" to "안녕"
        ),
        "thank_you" to mapOf(
            "en" to "Thank you",
            "ko" to "감사합니다"
        ),
        "sorry" to mapOf(
            "en" to "Sorry",
            "ko" to "죄송합니다"
        ),
        
        // Planning and analysis
        "plan_generation_failed" to mapOf(
            "en" to "Plan generation failed",
            "ko" to "계획 생성 실패"
        ),
        "error" to mapOf(
            "en" to "Error",
            "ko" to "오류"
        ),
        "validation_error" to mapOf(
            "en" to "Error during validation",
            "ko" to "검증 중 오류 발생"
        ),
        "problem" to mapOf(
            "en" to "Problem",
            "ko" to "문제"
        ),
        "suggestion" to mapOf(
            "en" to "Suggestion",
            "ko" to "제안"
        ),
        "estimated_time" to mapOf(
            "en" to "Estimated time",
            "ko" to "예상 시간"
        ),
        "minutes" to mapOf(
            "en" to "minutes",
            "ko" to "분"
        ),
        "dependencies" to mapOf(
            "en" to "Dependencies",
            "ko" to "의존성"
        ),
        
        // Agent and model terms
        "wizard_mode" to mapOf(
            "en" to "Wizard mode",
            "ko" to "위저드 모드"
        ),
        "normal_mode" to mapOf(
            "en" to "Normal mode",
            "ko" to "일반 모드"
        ),
        "visualization" to mapOf(
            "en" to "Visualization",
            "ko" to "시각화"
        ),
        "graph" to mapOf(
            "en" to "Graph",
            "ko" to "그래프"
        ),
        "chart" to mapOf(
            "en" to "Chart",
            "ko" to "차트"
        ),
        "diagram" to mapOf(
            "en" to "Diagram",
            "ko" to "다이어그램"
        ),
        "code" to mapOf(
            "en" to "Code",
            "ko" to "코드"
        ),
        "programming" to mapOf(
            "en" to "Programming",
            "ko" to "프로그래밍"
        ),
        "algorithm" to mapOf(
            "en" to "Algorithm",
            "ko" to "알고리즘"
        ),
        "complex" to mapOf(
            "en" to "Complex",
            "ko" to "복잡한"
        ),
        "analysis" to mapOf(
            "en" to "Analysis",
            "ko" to "분석"
        ),
        "design" to mapOf(
            "en" to "Design",
            "ko" to "설계"
        ),
        "research" to mapOf(
            "en" to "Research",
            "ko" to "연구"
        ),
        "investigation" to mapOf(
            "en" to "Investigation",
            "ko" to "조사"
        ),
        "document" to mapOf(
            "en" to "Document",
            "ko" to "문서"
        ),
        
        // Tool Messages
        "tool.web_search.description" to mapOf(
            "en" to "Search information on the web",
            "ko" to "웹에서 정보를 검색합니다"
        ),
        "tool.web_search.param.query" to mapOf(
            "en" to "Search keywords",
            "ko" to "검색할 키워드"
        ),
        "tool.web_search.param.limit" to mapOf(
            "en" to "Number of search results",
            "ko" to "검색 결과 수"
        ),
        "tool.web_search.error.query_required" to mapOf(
            "en" to "Search keywords are required",
            "ko" to "검색 키워드가 필요합니다"
        ),
        "tool.web_search.error.search_failed" to mapOf(
            "en" to "Web search failed: %s",
            "ko" to "웹 검색 실패: %s"
        ),
        
        "tool.file_read.description" to mapOf(
            "en" to "Read file contents",
            "ko" to "파일을 읽습니다"
        ),
        "tool.file_read.param.path" to mapOf(
            "en" to "File path to read",
            "ko" to "읽을 파일 경로"
        ),
        "tool.file_read.error.path_required" to mapOf(
            "en" to "File path is required",
            "ko" to "파일 경로가 필요합니다"
        ),
        "tool.file_read.error.read_failed" to mapOf(
            "en" to "File reading failed: %s",
            "ko" to "파일 읽기 실패: %s"
        ),
        
        "tool.file_write.description" to mapOf(
            "en" to "Write content to file",
            "ko" to "파일에 내용을 씁니다"
        ),
        "tool.file_write.param.path" to mapOf(
            "en" to "File path to write",
            "ko" to "쓸 파일 경로"
        ),
        "tool.file_write.param.content" to mapOf(
            "en" to "File content",
            "ko" to "파일 내용"
        ),
        "tool.file_write.error.path_required" to mapOf(
            "en" to "File path is required",
            "ko" to "파일 경로가 필요합니다"
        ),
        "tool.file_write.error.content_required" to mapOf(
            "en" to "File content is required",
            "ko" to "파일 내용이 필요합니다"
        ),
        "tool.file_write.success" to mapOf(
            "en" to "File write completed",
            "ko" to "파일 쓰기 완료"
        ),
        "tool.file_write.error.write_failed" to mapOf(
            "en" to "File writing failed: %s",
            "ko" to "파일 쓰기 실패: %s"
        )
    )
    
    /**
     * Get message in the specified language
     */
    fun getMessage(key: String, language: String = "en", vararg args: Any): String {
        val messageMap = messages[key] ?: return "Message not found: $key"
        val template = messageMap[language] ?: messageMap["en"] ?: "Message not found: $key"
        
        return if (args.isNotEmpty()) {
            template.format(*args)
        } else {
            template
        }
    }
    
    /**
     * Get message based on current locale
     */
    fun getMessage(key: String, vararg args: Any): String {
        val language = when (Locale.getDefault().language) {
            "ko" -> "ko"
            else -> "en"
        }
        return getMessage(key, language, *args)
    }
    
    /**
     * Get bilingual message (English + Korean)
     */
    fun getBilingualMessage(key: String, vararg args: Any): String {
        val english = getMessage(key, "en", *args)
        val korean = getMessage(key, "ko", *args)
        return "$english / $korean"
    }
    
    /**
     * Check if a locale prefers Korean
     */
    fun isKoreanLocale(locale: Locale = Locale.getDefault()): Boolean {
        return locale.language == "ko"
    }
    
    /**
     * Get language code from locale
     */
    fun getLanguageCode(locale: Locale = Locale.getDefault()): String {
        return when (locale.language) {
            "ko" -> "ko"
            else -> "en"
        }
    }
    
    /**
     * Get all available languages for a message key
     */
    fun getAvailableLanguages(key: String): Set<String> {
        return messages[key]?.keys ?: emptySet()
    }
    
    /**
     * Check if a message key exists
     */
    fun hasMessage(key: String): Boolean {
        return messages.containsKey(key)
    }
    
    /**
     * Get all message keys
     */
    fun getAllMessageKeys(): Set<String> {
        return messages.keys
    }
}

/**
 * Extension function for easy access to I18n messages
 */
fun String.i18n(vararg args: Any): String {
    return I18nMessages.getMessage(this, *args)
}

/**
 * Extension function for bilingual messages
 */
fun String.i18nBilingual(vararg args: Any): String {
    return I18nMessages.getBilingualMessage(this, *args)
}

/**
 * Extension function for specific language
 */
fun String.i18n(language: String, vararg args: Any): String {
    return I18nMessages.getMessage(this, language, *args)
} 