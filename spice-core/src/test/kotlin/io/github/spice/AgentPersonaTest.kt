package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AgentPersonaTest {
    
    @Test
    fun `test AgentPersona basic functionality`() {
        // Given: Create a persona
        val persona = PersonaLibrary.PROFESSIONAL_ASSISTANT
        
        // When: Apply persona to message
        val originalContent = "Hello, how can I help you today?"
        val personalizedContent = persona.applyToMessage(originalContent, MessageType.TEXT)
        
        // Then: Verify persona application
        assertTrue(personalizedContent.contains("Good day"))
        assertTrue(personalizedContent.contains("helpful"))
        assertTrue(personalizedContent.length > originalContent.length)
    }
    
    @Test
    fun `test different personality types`() {
        // Test Professional
        val professional = PersonaLibrary.PROFESSIONAL_ASSISTANT
        val professionalResponse = professional.applyToMessage("Task completed", MessageType.TEXT)
        assertTrue(professionalResponse.contains("Good day"))
        
        // Test Friendly
        val friendly = PersonaLibrary.FRIENDLY_BUDDY
        val friendlyResponse = friendly.applyToMessage("Task completed", MessageType.TEXT)
        assertTrue(friendlyResponse.contains("Hey there"))
        
        // Test Sarcastic
        val sarcastic = PersonaLibrary.SARCASTIC_EXPERT
        val sarcasticResponse = sarcastic.applyToMessage("Task completed", MessageType.TEXT)
        assertTrue(sarcasticResponse.contains("obviously"))
    }
    
    @Test
    fun `test PersonalityTrait transformations`() {
        // Test HumorousTrait
        val humorousTrait = HumorousTrait(0.8)
        val humorousResult = humorousTrait.transform("This is a test")
        // Should add humor elements with high intensity
        
        // Test PoliteTrait
        val politeTrait = PoliteTrait(0.9)
        val politeResult = politeTrait.transform("Can you help me")
        // Should add polite elements
        
        // Test ConciseTrait
        val conciseTrait = ConciseTrait(0.7)
        val conciseResult = conciseTrait.transform("This is a very long message that needs to be shortened")
        assertTrue(conciseResult.length <= "This is a very long message that needs to be shortened".length)
    }
    
    @Test
    fun `test PersonalizedAgent with persona`() = runBlocking {
        // Given: Create a base agent
        val baseAgent = object : BaseAgent(
            id = "test-agent",
            name = "Test Agent",
            description = "Test agent for persona testing"
        ) {
            override suspend fun processMessage(message: Message): Message {
                return Message(
                    id = "response-${message.id}",
                    type = MessageType.TEXT,
                    content = "Standard response: ${message.content}",
                    agentId = id,
                    parentId = message.id
                )
            }
        }
        
        // When: Apply persona to agent
        val personalizedAgent = baseAgent.withPersona(PersonaLibrary.CREATIVE_GENIUS)
        
        val testMessage = Message(
            id = "test-msg",
            type = MessageType.TEXT,
            content = "Create something new",
            agentId = "user"
        )
        
        val response = personalizedAgent.processMessage(testMessage)
        
        // Then: Verify persona application
        assertTrue(response.content.contains("✨") || response.content.contains("brilliant"))
        assertEquals("${baseAgent.id}-personalized", personalizedAgent.id)
        assertTrue(personalizedAgent.name.contains("Creative Genius"))
        assertEquals("creative_genius", response.metadata["persona"])
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
    fun `test PersonaVocabulary enrichment`() {
        val vocabulary = PersonaVocabulary()
        
        // Test formal style
        val formalResult = vocabulary.enrichResponse(
            "gonna help you", 
            PersonalityType.PROFESSIONAL, 
            CommunicationStyle.FORMAL
        )
        assertTrue(formalResult.contains("going to"))
        assertTrue(formalResult.endsWith("."))
        
        // Test casual style
        val casualResult = vocabulary.enrichResponse(
            "going to help you", 
            PersonalityType.FRIENDLY, 
            CommunicationStyle.CASUAL
        )
        assertTrue(casualResult.contains("gonna"))
        
        // Test humorous style
        val humorousResult = vocabulary.enrichResponse(
            "That's good", 
            PersonalityType.PLAYFUL, 
            CommunicationStyle.HUMOROUS
        )
        // Should potentially add emoji
    }
    
    @Test
    fun `test error message personalization`() {
        val professionalPersona = PersonaLibrary.PROFESSIONAL_ASSISTANT
        val friendlyPersona = PersonaLibrary.FRIENDLY_BUDDY
        
        val errorMessage = "Operation failed"
        
        val professionalError = professionalPersona.applyToMessage(errorMessage, MessageType.ERROR)
        val friendlyError = friendlyPersona.applyToMessage(errorMessage, MessageType.ERROR)
        
        // Professional should be more formal
        assertTrue(professionalError.contains("apologize") || professionalError.contains("inconvenience"))
        
        // Friendly should be more casual
        assertTrue(friendlyError.contains("Oops") || friendlyError.contains("my bad"))
    }
    
    @Test
    fun `test trait intensity effects`() {
        // High intensity humor
        val highHumor = HumorousTrait(0.9)
        val highHumorResult = highHumor.transform("This is serious")
        
        // Low intensity humor
        val lowHumor = HumorousTrait(0.2)
        val lowHumorResult = lowHumor.transform("This is serious")
        
        // High intensity should be more likely to add humor
        // Low intensity should rarely add humor
        assertEquals("This is serious", lowHumorResult) // Should remain unchanged
    }
    
    @Test
    fun `test PersonalizedAgent trait checking`() = runBlocking {
        val baseAgent = object : BaseAgent("test", "Test", "Test") {
            override suspend fun processMessage(message: Message): Message {
                return Message(
                    id = "test-response",
                    type = MessageType.TEXT,
                    content = "Response",
                    agentId = id
                )
            }
        }
        
        val personalizedAgent = baseAgent.withPersona(PersonaLibrary.CREATIVE_GENIUS)
        
        // Creative genius should have creative and humorous traits
        assertTrue(personalizedAgent.hasTrait("creative"))
        assertTrue(personalizedAgent.hasTrait("humorous"))
        assertFalse(personalizedAgent.hasTrait("concise"))
    }
    
    @Test
    fun `test all predefined personas`() {
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