package io.github.spice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.Locale

/**
 * 🧪 I18nMessages Test Suite
 * 
 * Comprehensive tests for internationalization support
 */
class I18nMessagesTest {
    
    @Test
    fun `basic message retrieval test`() {
        // Given & When
        val englishMessage = I18nMessages.getMessage("error.processing", "en", "test error")
        val koreanMessage = I18nMessages.getMessage("error.processing", "ko", "test error")
        
        // Then
        assertEquals("Sorry, an error occurred during processing: test error", englishMessage)
        assertEquals("죄송합니다. 처리 중 오류가 발생했습니다: test error", koreanMessage)
    }
    
    @Test
    fun `locale-based message retrieval test`() {
        // Given
        val originalLocale = Locale.getDefault()
        
        try {
            // When - English locale
            Locale.setDefault(Locale.ENGLISH)
            val englishMessage = I18nMessages.getMessage("error.system", "system error")
            
            // When - Korean locale
            Locale.setDefault(Locale.KOREAN)
            val koreanMessage = I18nMessages.getMessage("error.system", "system error")
            
            // Then
            assertEquals("Sorry, a system error occurred: system error", englishMessage)
            assertEquals("죄송합니다. 시스템 오류가 발생했습니다: system error", koreanMessage)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
    
    @Test
    fun `bilingual message test`() {
        // Given & When
        val bilingualMessage = I18nMessages.getBilingualMessage("error.connection", "network issue")
        
        // Then
        assertTrue(bilingualMessage.contains("Sorry, a connection error occurred: network issue"))
        assertTrue(bilingualMessage.contains("죄송합니다. 연결 오류가 발생했습니다: network issue"))
        assertTrue(bilingualMessage.contains(" / "))
    }
    
    @Test
    fun `extension function test`() {
        // Given & When
        val message = "error.generation".i18n("test error")
        val bilingualMessage = "error.claude.generation".i18nBilingual("claude error")
        val specificLanguage = "error.vllm.processing".i18n("ko", "vllm error")
        
        // Then
        assertTrue(message.contains("Sorry, an error occurred while generating response: test error"))
        assertTrue(bilingualMessage.contains("Sorry, Claude encountered an error"))
        assertTrue(bilingualMessage.contains("Claude가 응답을 생성하는 중 오류가 발생했습니다"))
        assertEquals("죄송합니다. vLLM 서버 처리 중 오류가 발생했습니다: vllm error", specificLanguage)
    }
    
    @Test
    fun `session message test`() {
        // Given & When
        val codingSession = I18nMessages.getMessage("session.coding.started", "en", "Python")
        val learningSession = I18nMessages.getMessage("session.learning.started", "ko", "Machine Learning")
        
        // Then
        assertEquals("🚀 vLLM coding session has started! Please ask questions about Python.", codingSession)
        assertEquals("🌐 OpenRouter 학습 세션이 시작되었습니다! Machine Learning 에 대해 무엇을 배우고 싶으신가요?", learningSession)
    }
    
    @Test
    fun `status message test`() {
        // Given & When
        val modelStatus = I18nMessages.getMessage("status.model", "en", "gpt-4")
        val contextStatus = I18nMessages.getMessage("status.context", "ko", 5, 1000)
        val systemPromptActive = I18nMessages.getMessage("status.system_prompt_active", "en")
        
        // Then
        assertEquals("Model: gpt-4", modelStatus)
        assertEquals("컨텍스트: 5개 메시지, 1000개 토큰", contextStatus)
        assertEquals("System Prompt: Active", systemPromptActive)
    }
    
    @Test
    fun `planning message test`() {
        // Given & When
        val goalAnalysis = I18nMessages.getMessage("planning.goal_analysis", "en")
        val goal = I18nMessages.getMessage("planning.goal", "ko", "Learn Kotlin")
        val estimatedTime = I18nMessages.getMessage("planning.estimated_time", "en", 30)
        
        // Then
        assertEquals("🎯 GOAL ANALYSIS AND PLANNING", goalAnalysis)
        assertEquals("목표: Learn Kotlin", goal)
        assertEquals("⏱️ Estimated time: 30 minutes", estimatedTime)
    }
    
    @Test
    fun `cost message test`() {
        // Given & When
        val estimatedCost = I18nMessages.getMessage("cost.estimated", "en", "0.001234")
        val calculationFailed = I18nMessages.getMessage("cost.calculation_failed", "ko")
        val pricingUnavailable = I18nMessages.getMessage("cost.pricing_info_unavailable", "en")
        
        // Then
        assertEquals("Estimated cost: $0.001234", estimatedCost)
        assertEquals("비용 계산 불가", calculationFailed)
        assertEquals("Pricing information unavailable", pricingUnavailable)
    }
    
    @Test
    fun `fallback to english test`() {
        // Given & When - Request unsupported language
        val message = I18nMessages.getMessage("error.processing", "fr", "test error")
        
        // Then - Should fallback to English
        assertEquals("Sorry, an error occurred during processing: test error", message)
    }
    
    @Test
    fun `missing message key test`() {
        // Given & When
        val message = I18nMessages.getMessage("nonexistent.key", "en")
        
        // Then
        assertEquals("Message not found: nonexistent.key", message)
    }
    
    @Test
    fun `locale detection test`() {
        // Given
        val koreanLocale = Locale.KOREAN
        val englishLocale = Locale.ENGLISH
        val japaneseLocale = Locale.JAPANESE
        
        // When & Then
        assertTrue(I18nMessages.isKoreanLocale(koreanLocale))
        assertFalse(I18nMessages.isKoreanLocale(englishLocale))
        assertFalse(I18nMessages.isKoreanLocale(japaneseLocale))
        
        assertEquals("ko", I18nMessages.getLanguageCode(koreanLocale))
        assertEquals("en", I18nMessages.getLanguageCode(englishLocale))
        assertEquals("en", I18nMessages.getLanguageCode(japaneseLocale)) // fallback
    }
    
    @Test
    fun `available languages test`() {
        // Given & When
        val languages = I18nMessages.getAvailableLanguages("error.processing")
        val nonexistentLanguages = I18nMessages.getAvailableLanguages("nonexistent.key")
        
        // Then
        assertTrue(languages.contains("en"))
        assertTrue(languages.contains("ko"))
        assertEquals(2, languages.size)
        assertTrue(nonexistentLanguages.isEmpty())
    }
    
    @Test
    fun `message existence test`() {
        // Given & When & Then
        assertTrue(I18nMessages.hasMessage("error.processing"))
        assertTrue(I18nMessages.hasMessage("session.coding.started"))
        assertTrue(I18nMessages.hasMessage("cost.estimated"))
        assertFalse(I18nMessages.hasMessage("nonexistent.key"))
    }
    
    @Test
    fun `all message keys test`() {
        // Given & When
        val allKeys = I18nMessages.getAllMessageKeys()
        
        // Then
        assertTrue(allKeys.isNotEmpty())
        assertTrue(allKeys.contains("error.processing"))
        assertTrue(allKeys.contains("session.coding.started"))
        assertTrue(allKeys.contains("planning.goal_analysis"))
        assertTrue(allKeys.contains("cost.estimated"))
    }
    
    @Test
    fun `message formatting test`() {
        // Given & When
        val noArgs = I18nMessages.getMessage("status.system_prompt_active", "en")
        val oneArg = I18nMessages.getMessage("status.model", "en", "gpt-4")
        val multipleArgs = I18nMessages.getMessage("status.context", "en", 10, 2000)
        
        // Then
        assertEquals("System Prompt: Active", noArgs)
        assertEquals("Model: gpt-4", oneArg)
        assertEquals("Context: 10 messages, 2000 tokens", multipleArgs)
    }
} 