package io.github.noailabs.spice.springboot.statemachine.transformer

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import org.slf4j.LoggerFactory

/**
 * Injects authentication context into messages.
 *
 * Detects logged-in/logged-out state and adds isLoggedIn metadata.
 * Use this for routing to different graph branches based on auth state.
 *
 * **Example - kai-core login/logout routing:**
 * ```kotlin
 * @Bean
 * fun authContextTransformer(): MessageTransformer {
 *     return AuthContextTransformer { message ->
 *         // Check if user has valid session token
 *         val sessionToken = message.getMetadata<String>("sessionToken")
 *         sessionToken != null && sessionService.isValid(sessionToken)
 *     }
 * }
 * ```
 *
 * **Then in Graph DSL:**
 * ```kotlin
 * val graph = graph("super-graph") {
 *     agent("router", routerAgent)
 *
 *     // Route to logged-in subgraph
 *     subgraph("logged-in", loggedInGraph) { message ->
 *         message.getMetadata<Boolean>("isLoggedIn") == true
 *     }
 *
 *     // Route to logged-out subgraph
 *     subgraph("logged-out", loggedOutGraph) { message ->
 *         message.getMetadata<Boolean>("isLoggedIn") != true
 *     }
 * }
 * ```
 *
 * @param authDetector Function to detect if user is logged in
 */
class AuthContextTransformer(
    private val authDetector: (SpiceMessage) -> Boolean
) : MessageTransformer {

    private val logger = LoggerFactory.getLogger(AuthContextTransformer::class.java)

    override suspend fun beforeExecution(
        graph: Graph,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val isLoggedIn = try {
            authDetector(message)
        } catch (e: Exception) {
            logger.error("Auth detection failed, defaulting to logged-out", e)
            false
        }

        logger.debug(
            "Auth context detected for graph ${graph.id}: isLoggedIn=$isLoggedIn, userId=${message.getMetadata<String>("userId")}"
        )

        return SpiceResult.success(
            message.withMetadata(
                mapOf(
                    "isLoggedIn" to isLoggedIn,
                    "authCheckedAt" to System.currentTimeMillis()
                )
            )
        )
    }

    companion object {
        /**
         * Creates an AuthContextTransformer that checks for userId metadata.
         */
        fun byUserId(): AuthContextTransformer {
            return AuthContextTransformer { message ->
                message.getMetadata<String>("userId") != null
            }
        }

        /**
         * Creates an AuthContextTransformer that checks for session token.
         */
        fun bySessionToken(): AuthContextTransformer {
            return AuthContextTransformer { message ->
                message.getMetadata<String>("sessionToken") != null
            }
        }

        /**
         * Creates an AuthContextTransformer that always returns logged-in (for testing).
         */
        fun alwaysLoggedIn(): AuthContextTransformer {
            return AuthContextTransformer { true }
        }

        /**
         * Creates an AuthContextTransformer that always returns logged-out (for testing).
         */
        fun alwaysLoggedOut(): AuthContextTransformer {
            return AuthContextTransformer { false }
        }
    }
}
