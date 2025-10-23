package io.github.noailabs.spice.context

import io.github.noailabs.spice.AgentContext
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * 🎯 Context Extension System Tests
 *
 * @since 0.4.0
 */
class ContextExtensionTest {

    @BeforeTest
    fun setup() {
        ContextExtensionRegistry.clear()
    }

    @Test
    fun `TenantContextExtension should enrich context with tenant config`() = runTest {
        // Given
        val extension = TenantContextExtension { tenantId ->
            mapOf(
                "features" to listOf("feature1", "feature2"),
                "limits" to mapOf("maxRequests" to 1000)
            )
        }

        val baseContext = AgentContext.of("tenantId" to "CHIC")

        // When
        val enriched = extension.enrich(baseContext)

        // Then
        assertEquals("CHIC", enriched.tenantId)
        assertNotNull(enriched.get("tenant_config"))
        assertNotNull(enriched.get("tenant_features"))
    }

    @Test
    fun `UserContextExtension should enrich context with user data`() = runTest {
        // Given
        val extension = UserContextExtension { userId ->
            mapOf(
                "email" to "$userId@example.com",
                "permissions" to listOf("read", "write")
            )
        }

        val baseContext = AgentContext.of("userId" to "user-123")

        // When
        val enriched = extension.enrich(baseContext)

        // Then
        assertEquals("user-123", enriched.userId)
        assertNotNull(enriched.get("user_profile"))
        assertNotNull(enriched.get("user_permissions"))
    }

    @Test
    fun `SessionContextExtension should enrich context with session data`() = runTest {
        // Given
        val extension = SessionContextExtension { sessionId ->
            mapOf(
                "startedAt" to System.currentTimeMillis(),
                "deviceType" to "mobile"
            )
        }

        val baseContext = AgentContext.of("sessionId" to "sess-456")

        // When
        val enriched = extension.enrich(baseContext)

        // Then
        assertEquals("sess-456", enriched.sessionId)
        assertNotNull(enriched.get("session_data"))
    }

    @Test
    fun `ContextExtensionRegistry should register and apply extensions`() = runTest {
        // Given
        val tenantExt = TenantContextExtension { mapOf("config" to "tenant-config") }
        val userExt = UserContextExtension { mapOf("profile" to "user-profile") }

        ContextExtensionRegistry.register(tenantExt)
        ContextExtensionRegistry.register(userExt)

        val baseContext = AgentContext.of(
            "tenantId" to "ACME",
            "userId" to "user-789"
        )

        // When
        val enriched = ContextExtensionRegistry.enrichContext(baseContext)

        // Then
        assertEquals("ACME", enriched.tenantId)
        assertEquals("user-789", enriched.userId)
        assertNotNull(enriched.get("tenant_config"))
        assertNotNull(enriched.get("user_profile"))
    }

    @Test
    fun `ContextExtensionRegistry should handle extension failures gracefully`() = runTest {
        // Given
        val failingExtension = object : ContextExtension {
            override val key = "failing"
            override suspend fun enrich(context: AgentContext): AgentContext {
                throw RuntimeException("Extension failed")
            }
        }

        ContextExtensionRegistry.register(failingExtension)
        val baseContext = AgentContext.of("tenantId" to "TEST")

        // When
        val enriched = ContextExtensionRegistry.enrichContext(baseContext)

        // Then: Should not throw, just skip failed extension
        assertEquals("TEST", enriched.tenantId)
    }

    @Test
    fun `ContextExtensionRegistry should allow unregistration`() = runTest {
        // Given
        val extension = TenantContextExtension { mapOf("config" to "test") }
        ContextExtensionRegistry.register(extension)
        assertTrue(ContextExtensionRegistry.has("tenant"))

        // When
        ContextExtensionRegistry.unregister("tenant")

        // Then
        assertFalse(ContextExtensionRegistry.has("tenant"))
    }

    @Test
    fun `Custom ContextExtension should work`() = runTest {
        // Given
        class FeatureFlagExtension : ContextExtension {
            override val key = "features"

            override suspend fun enrich(context: AgentContext): AgentContext {
                val tenantId = context.tenantId ?: return context
                val flags = mapOf(
                    "newUI" to true,
                    "betaFeatures" to false
                )
                return context.with("featureFlags", flags)
            }
        }

        val extension = FeatureFlagExtension()
        ContextExtensionRegistry.register(extension)

        val baseContext = AgentContext.of("tenantId" to "BETA")

        // When
        val enriched = ContextExtensionRegistry.enrichContext(baseContext)

        // Then
        assertNotNull(enriched.get("featureFlags"))
    }

    @Test
    fun `Extensions should be applied in registration order`() = runTest {
        // Given
        val ext1 = object : ContextExtension {
            override val key = "ext1"
            override suspend fun enrich(context: AgentContext): AgentContext {
                return context.with("order", listOf("ext1"))
            }
        }

        val ext2 = object : ContextExtension {
            override val key = "ext2"
            override suspend fun enrich(context: AgentContext): AgentContext {
                val order = context.getAs<List<String>>("order") ?: emptyList()
                return context.with("order", order + "ext2")
            }
        }

        ContextExtensionRegistry.register(ext1)
        ContextExtensionRegistry.register(ext2)

        // When
        val enriched = ContextExtensionRegistry.enrichContext(AgentContext.empty())

        // Then
        assertEquals(listOf("ext1", "ext2"), enriched.get("order"))
    }
}
