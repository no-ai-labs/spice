package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimpleAgentPersonaTest {
    
    @Test
    fun `test AgentPersona basic functionality`() {
        // Given: Create a persona
        val persona = PersonaLibrary.PROFESSIONAL_ASSISTANT
        
        // When: Apply persona to message
        val originalContent = "Hello, how can I help you today?"
        val personalizedContent = persona.applyToMessage(originalContent, MessageType.TEXT)
        
        // Then: Verify persona application
        assertNotNull(personalizedContent)
        assertTrue(personalizedContent.isNotEmpty())
        // Professional persona should add formal elements
        assertTrue(personalizedContent.contains("Good day") || personalizedContent.contains("helpful"))
    }
    
    @Test
    fun `test different personality types produce different responses`() {
        val testMessage = "Task completed"
        
        // Test Professional
        val professional = PersonaLibrary.PROFESSIONAL_ASSISTANT
        val professionalResponse = professional.applyToMessage(testMessage, MessageType.TEXT)
        
        // Test Friendly
        val friendly = PersonaLibrary.FRIENDLY_BUDDY
        val friendlyResponse = friendly.applyToMessage(testMessage, MessageType.TEXT)
        
        // Test Sarcastic
        val sarcastic = PersonaLibrary.SARCASTIC_EXPERT
        val sarcasticResponse = sarcastic.applyToMessage(testMessage, MessageType.TEXT)
        
        // All should be different
        assertNotNull(professionalResponse)
        assertNotNull(friendlyResponse)
        assertNotNull(sarcasticResponse)
        
        // Professional should be more formal
        assertTrue(professionalResponse.contains("Good day") || professionalResponse.contains("helpful"))
        
        // Friendly should be more casual
        assertTrue(friendlyResponse.contains("Hey") || friendlyResponse.contains("!"))
        
        // Sarcastic should have different tone
        assertTrue(sarcasticResponse.contains("obviously") || sarcasticResponse.contains("Well"))
    }
    
    @Test
    fun `test PersonalityTrait transformations`() {
        // Test HumorousTrait
        val humorousTrait = HumorousTrait(0.8)
        val originalText = "This is a test"
        val humorousResult = humorousTrait.transform(originalText)
        
        // Should either add humor or stay the same
        assertNotNull(humorousResult)
        assertTrue(humorousResult.length >= originalText.length)
        
        // Test PoliteTrait
        val politeTrait = PoliteTrait(0.9)
        val politeResult = politeTrait.transform("Can you help me")
        
        // Should be polite
        assertNotNull(politeResult)
        assertTrue(politeResult.isNotEmpty())
        
        // Test ConciseTrait
        val conciseTrait = ConciseTrait(0.7)
        val longMessage = "This is a very long message that needs to be shortened because it contains too much information"
        val conciseResult = conciseTrait.transform(longMessage)
        
        // Should be shorter or same length
        assertNotNull(conciseResult)
        assertTrue(conciseResult.length <= longMessage.length)
    }
    
    @Test
    fun `test confidence modification`() {
        // Test different personality types affecting confidence
        val assertivePersona = buildPersona("Assertive") {
            personalityType = PersonalityType.ASSERTIVE
        }
        
        val humblePersona = buildPersona("Humble") {
            personalityType = PersonalityType.HUMBLE
        }
        
        val professionalPersona = buildPersona("Professional") {
            personalityType = PersonalityType.PROFESSIONAL
        }
        
        val baseConfidence = 0.8
        
        // Assertive should increase confidence
        assertTrue(assertivePersona.modifyConfidence(baseConfidence) > baseConfidence)
        
        // Humble should decrease confidence
        assertTrue(humblePersona.modifyConfidence(baseConfidence) < baseConfidence)
        
        // Professional should slightly increase confidence
        assertTrue(professionalPersona.modifyConfidence(baseConfidence) > baseConfidence)
    }
    
    @Test
    fun `test buildPersona DSL`() {
        // Given: Build custom persona using DSL
        val customPersona = buildPersona("Custom Tester") {
            personalityType = PersonalityType.ANALYTICAL
            communicationStyle = CommunicationStyle.TECHNICAL
            
            trait(HumorousTrait(0.3))
            trait(PoliteTrait(0.8))
            
            responsePattern(MessageType.TEXT, ResponsePattern(
                prefix = "Analysis:",
                suffix = "Data processed.",
                formalityLevel = 0.8
            ))
            
            behaviorModifier("precision", 0.9)
        }
        
        // When: Apply custom persona
        val response = customPersona.applyToMessage("Process this data", MessageType.TEXT)
        
        // Then: Verify custom persona
        assertEquals("Custom Tester", customPersona.name)
        assertEquals(PersonalityType.ANALYTICAL, customPersona.personalityType)
        assertEquals(CommunicationStyle.TECHNICAL, customPersona.communicationStyle)
        assertTrue(response.startsWith("Analysis:"))
        assertTrue(response.endsWith("Data processed."))
        assertEquals(0.9, customPersona.getBehaviorModifier("precision"))
    }
    
    @Test
    fun `test all predefined personas exist and work`() {
        val personas = listOf(
            PersonaLibrary.PROFESSIONAL_ASSISTANT,
            PersonaLibrary.FRIENDLY_BUDDY,
            PersonaLibrary.SARCASTIC_EXPERT,
            PersonaLibrary.CREATIVE_GENIUS,
            PersonaLibrary.WISE_MENTOR
        )
        
        personas.forEach { persona ->
            // Each persona should have basic properties
            assertTrue(persona.name.isNotEmpty())
            assertTrue(persona.traits.isNotEmpty())
            assertTrue(persona.responsePatterns.isNotEmpty())
            assertNotNull(persona.vocabulary)
            
            // Should be able to process messages
            val result = persona.applyToMessage("Test message", MessageType.TEXT)
            assertTrue(result.isNotEmpty())
        }
    }
} 