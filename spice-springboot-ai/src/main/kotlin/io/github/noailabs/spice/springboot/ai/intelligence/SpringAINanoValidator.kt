package io.github.noailabs.spice.springboot.ai.intelligence

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.ClarificationOption
import io.github.noailabs.spice.intelligence.ClarificationReason
import io.github.noailabs.spice.intelligence.ClarificationRequest
import io.github.noailabs.spice.intelligence.spi.*
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel

/**
 * Spring AI 기반 Nano Validator 구현체
 *
 * GPT-4o-mini 또는 동급의 경량 모델을 사용하여 Fast Layer 결과를 검증.
 *
 * ## 프롬프트 전략
 * - Structured Output (JSON) 사용
 * - 짧은 프롬프트 (토큰 효율성)
 * - 명확한 판단 기준 제시
 *
 * ## 상태 반환 기준
 * - OVERRIDE: confidence >= 0.7 && 명확한 매칭
 * - CLARIFY: 모호함 감지 || confidence 0.5-0.7
 * - DELEGATE: 복잡한 케이스 || confidence < 0.5 || 정책 위반 의심
 * - FALLBACK: 파싱 오류 || 타임아웃
 *
 * @property chatClient Spring AI ChatClient
 * @property modelId 모델 식별자
 *
 * @since 2.0.0
 */
class SpringAINanoValidator(
    private val chatClient: ChatClient,
    override val modelId: String = "gpt-4o-mini"
) : NanoValidator {

    override val maxInputTokens: Int = 1024

    override suspend fun validate(request: NanoValidationRequest): SpiceResult<NanoValidationResult> {
        val startTime = System.currentTimeMillis()

        return try {
            val prompt = buildPrompt(request)
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content() ?: ""

            val latencyMs = System.currentTimeMillis() - startTime
            val result = parseResponse(response, request, latencyMs)

            SpiceResult.success(result)
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startTime
            SpiceResult.success(
                NanoValidationResult.fallback("Exception: ${e.message}", latencyMs)
            )
        }
    }

    private fun buildPrompt(request: NanoValidationRequest): String {
        val candidatesJson = request.candidates.take(5).joinToString(",\n") { candidate ->
            """    {"canonical": "${candidate.canonical}", "score": ${candidate.score}, "label": "${candidate.label ?: candidate.canonical}"}"""
        }

        val policyContext = if (request.policyHints.isNotEmpty()) {
            val policies = request.policyHints.take(2).joinToString("\n") {
                "- ${it.content.take(100)}"
            }
            "\n\nRelevant policies:\n$policies"
        } else ""

        return """You are a validation assistant. Analyze the user utterance and determine the best matching option.

User utterance: "${request.utterance}"

Candidates (ranked by semantic score):
[
$candidatesJson
]

Suggested: ${request.suggestedCanonical ?: "none"} (confidence: ${request.suggestedConfidence})
Gap between top 2: ${request.gap}
$policyContext

Respond in JSON format:
{
  "decision": "OVERRIDE" | "CLARIFY" | "DELEGATE",
  "canonical": "selected_canonical_if_override",
  "confidence": 0.0-1.0,
  "reasoning": "brief explanation"
}

Decision criteria:
- OVERRIDE: Clear match, confidence >= 0.7
- CLARIFY: Ambiguous, need user clarification
- DELEGATE: Complex case, policy concern, or low confidence"""
    }

    private fun parseResponse(
        response: String,
        request: NanoValidationRequest,
        latencyMs: Long
    ): NanoValidationResult {
        return try {
            // Simple JSON parsing (production에서는 kotlinx.serialization 사용 권장)
            val decision = extractJsonField(response, "decision")?.uppercase() ?: "DELEGATE"
            val canonical = extractJsonField(response, "canonical")
            val confidence = extractJsonField(response, "confidence")?.toDoubleOrNull() ?: 0.5
            val reasoning = extractJsonField(response, "reasoning")

            when (decision) {
                "OVERRIDE" -> {
                    if (canonical != null) {
                        NanoValidationResult.override(
                            canonical = canonical,
                            confidence = confidence,
                            reasoning = reasoning,
                            latencyMs = latencyMs
                        )
                    } else {
                        // canonical 없으면 suggested 사용
                        NanoValidationResult.override(
                            canonical = request.suggestedCanonical ?: request.candidates.firstOrNull()?.canonical ?: "unknown",
                            confidence = confidence,
                            reasoning = reasoning ?: "No canonical in response, using suggested",
                            latencyMs = latencyMs
                        )
                    }
                }

                "CLARIFY" -> {
                    val clarificationRequest = ClarificationRequest(
                        question = "다음 중 어떤 것을 원하시나요?",
                        options = request.candidates.take(3).map {
                            ClarificationOption.simple(
                                id = it.canonical,
                                label = it.label ?: it.canonical
                            )
                        },
                        reason = ClarificationReason.AMBIGUOUS,
                        metadata = mapOf("reasoning" to (reasoning ?: ""))
                    )
                    NanoValidationResult.clarify(
                        request = clarificationRequest,
                        confidence = confidence,
                        reasoning = reasoning,
                        latencyMs = latencyMs
                    )
                }

                else -> {
                    NanoValidationResult.delegate(
                        reason = reasoning ?: "Complex case requires Big LLM",
                        confidence = confidence,
                        reasoning = reasoning,
                        latencyMs = latencyMs
                    )
                }
            }
        } catch (e: Exception) {
            NanoValidationResult.fallback("Parse error: ${e.message}", latencyMs)
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"?([^",}\n]+)"?""".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)?.trim()?.removeSurrounding("\"")
    }

    companion object {
        /**
         * ChatModel에서 생성
         */
        fun from(chatModel: ChatModel, modelId: String = "gpt-4o-mini"): SpringAINanoValidator {
            return SpringAINanoValidator(
                chatClient = ChatClient.create(chatModel),
                modelId = modelId
            )
        }
    }
}
