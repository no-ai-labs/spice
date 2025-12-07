package io.github.noailabs.spice.springboot.ai.intelligence

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.*
import io.github.noailabs.spice.intelligence.spi.*
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel

/**
 * Spring AI 기반 One-Shot Reasoner 구현체
 *
 * GPT-4o 또는 동급의 고성능 모델을 사용하여 복합 의사결정 수행.
 *
 * ## 판단 항목
 * - domainRelevance: 도메인 관련성 (0.0-1.0)
 * - offDomain: Off-domain 여부 (hard/soft)
 * - intentShift: Intent 전환 감지
 * - workflowAction: 워크플로우 전환/재개
 * - aiCanonical: 최종 canonical 결정
 *
 * ## 프롬프트 전략
 * - Policy hints를 컨텍스트로 포함
 * - Session history 활용
 * - Structured JSON output
 *
 * @property chatClient Spring AI ChatClient
 * @property modelId 모델 식별자
 *
 * @since 2.0.0
 */
class SpringAIOneShotReasoner(
    private val chatClient: ChatClient,
    override val modelId: String = "gpt-4o"
) : OneShotReasoner {

    override val maxInputTokens: Int = 4096

    override suspend fun reason(request: OneShotRequest): SpiceResult<CompositeDecision> {
        val startTime = System.currentTimeMillis()

        return try {
            val prompt = buildPrompt(request)
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content() ?: ""

            val latencyMs = System.currentTimeMillis() - startTime
            val decision = parseResponse(response, request, latencyMs)

            SpiceResult.success(decision)
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startTime
            SpiceResult.success(createFallbackDecision(request, latencyMs, e.message))
        }
    }

    private fun buildPrompt(request: OneShotRequest): String {
        val optionsJson = request.options.joinToString(",\n") { option ->
            """    {"canonical": "${option.canonical}", "description": "${option.description ?: option.canonical}"}"""
        }

        val semanticHint = request.semanticScores?.let {
            """
Semantic Analysis:
- Top candidate: ${it.topCanonical} (score: ${it.topScore})
- Second candidate score: ${it.secondScore}
- Gap: ${it.gap}"""
        } ?: ""

        val policyContext = if (request.policyHints.isNotEmpty()) {
            val policies = request.policyHints.joinToString("\n") {
                "- [${it.policyType}] ${it.content.take(200)}"
            }
            """
Relevant Policies:
$policies"""
        } else ""

        val sessionContext = """
Session Context:
- Clarification attempts: ${request.sessionContext.clarifyAttempts}/${request.sessionContext.maxClarifyAttempts}
- Frustration detected: ${request.sessionContext.frustrationDetected}
- Recent utterances: ${request.sessionContext.utteranceHistory.takeLast(3).joinToString(" → ")}"""

        val availableWorkflows = if (request.availableWorkflows.isNotEmpty()) {
            "\nAvailable workflows for switch: ${request.availableWorkflows.joinToString(", ")}"
        } else ""

        return """You are an intelligent routing assistant. Analyze the user's intent and make a routing decision.

Current Context:
- Workflow: ${request.workflowId ?: "none"}
- Current node: ${request.currentNodeId ?: "none"}
- Previous nodes: ${request.prevNodes.takeLast(5).joinToString(" → ")}

User utterance: "${request.utterance}"

Available options:
[
$optionsJson
]
$semanticHint
$policyContext
$sessionContext
$availableWorkflows

Respond in JSON format:
{
  "domainRelevance": 0.0-1.0,
  "offDomainType": "none" | "soft" | "hard",
  "intentShift": true | false,
  "workflowAction": "none" | "switch" | "resume",
  "targetWorkflow": "workflow_id_if_switch",
  "aiCanonical": "selected_canonical_or_null",
  "confidence": 0.0-1.0,
  "routingSignal": "NORMAL" | "AMBIGUOUS" | "OFF_DOMAIN_SOFT" | "OFF_DOMAIN_HARD" | "SWITCH_WORKFLOW",
  "reasoning": "brief explanation"
}

Decision criteria:
- Check if utterance is relevant to current domain/workflow
- If off-domain, determine soft (can guide back) vs hard (should block)
- Detect intent shifts (user wants to do something else)
- Select best canonical if in-domain, or null if off-domain
- Consider policy constraints"""
    }

    private fun parseResponse(
        response: String,
        request: OneShotRequest,
        latencyMs: Long
    ): CompositeDecision {
        return try {
            val domainRelevance = extractJsonField(response, "domainRelevance")?.toDoubleOrNull() ?: 0.5
            val offDomainType = extractJsonField(response, "offDomainType") ?: "none"
            val intentShift = extractJsonField(response, "intentShift")?.toBooleanStrictOrNull() ?: false
            val workflowAction = extractJsonField(response, "workflowAction") ?: "none"
            val targetWorkflow = extractJsonField(response, "targetWorkflow")
            val aiCanonical = extractJsonField(response, "aiCanonical")?.takeIf { it != "null" }
            val confidence = extractJsonField(response, "confidence")?.toDoubleOrNull() ?: 0.5
            val routingSignalStr = extractJsonField(response, "routingSignal") ?: "NORMAL"
            val reasoning = extractJsonField(response, "reasoning")

            val routingSignal = parseRoutingSignal(routingSignalStr, offDomainType, intentShift, workflowAction)

            CompositeDecision(
                domainRelevance = domainRelevance,
                isOffDomain = offDomainType != "none",
                aiCanonical = aiCanonical,
                confidence = confidence,
                intentShift = intentShift,
                newWorkflowId = if (workflowAction == "switch") targetWorkflow else null,
                resumeNodeId = if (workflowAction == "resume") targetWorkflow else null,
                reasoning = reasoning ?: "OneShotReasoner decision",
                decisionSource = DecisionSource.SLM,
                routingSignal = routingSignal,
                latencyMs = latencyMs
            )
        } catch (e: Exception) {
            createFallbackDecision(request, latencyMs, "Parse error: ${e.message}")
        }
    }

    private fun parseRoutingSignal(
        signalStr: String,
        offDomainType: String,
        intentShift: Boolean,
        workflowAction: String
    ): RoutingSignal {
        return when {
            offDomainType == "hard" -> RoutingSignal.OFF_DOMAIN_HARD
            offDomainType == "soft" -> RoutingSignal.OFF_DOMAIN_SOFT
            workflowAction == "switch" -> RoutingSignal.SWITCH_WORKFLOW
            workflowAction == "resume" -> RoutingSignal.RESUME_WORKFLOW
            else -> try {
                RoutingSignal.valueOf(signalStr.uppercase())
            } catch (e: Exception) {
                RoutingSignal.NORMAL
            }
        }
    }

    private fun createFallbackDecision(
        request: OneShotRequest,
        latencyMs: Long,
        errorMessage: String?
    ): CompositeDecision {
        // Fallback: semantic scores 기반으로 결정
        val semantic = request.semanticScores
        return CompositeDecision(
            domainRelevance = 0.5,  // 중립
            aiCanonical = semantic?.topCanonical,
            confidence = (semantic?.topScore ?: 0.5) * 0.8,  // 20% 감소
            reasoning = "Fallback: $errorMessage. Using semantic result.",
            decisionSource = DecisionSource.SLM,
            routingSignal = if ((semantic?.topScore ?: 0.0) >= 0.65) RoutingSignal.NORMAL else RoutingSignal.AMBIGUOUS,
            latencyMs = latencyMs
        )
    }

    private fun extractJsonField(json: String, field: String): String? {
        // Handle both string and non-string values
        val stringPattern = """"$field"\s*:\s*"([^"]*)"""""".toRegex()
        val valuePattern = """"$field"\s*:\s*([^,}\n]+)""".toRegex()

        return stringPattern.find(json)?.groupValues?.getOrNull(1)
            ?: valuePattern.find(json)?.groupValues?.getOrNull(1)?.trim()
    }

    companion object {
        /**
         * ChatModel에서 생성
         */
        fun from(chatModel: ChatModel, modelId: String = "gpt-4o"): SpringAIOneShotReasoner {
            return SpringAIOneShotReasoner(
                chatClient = ChatClient.create(chatModel),
                modelId = modelId
            )
        }
    }
}
