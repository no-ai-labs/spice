package io.github.noailabs.spice.intelligence

/**
 * Standard metric names and labels for Intelligence Layer decisions.
 *
 * Use these constants for consistent observability across implementations.
 * Implementations should use these labels when reporting to Micrometer, Prometheus, etc.
 *
 * ## Metric Naming Convention
 * - Prefix: `spice_intelligence_`
 * - Suffix indicates type: `_total` (counter), `_seconds` (histogram/timer)
 *
 * ## Example Usage
 * ```kotlin
 * // Micrometer
 * meterRegistry.counter(
 *     IntelligenceMetrics.DECISION_TOTAL,
 *     IntelligenceMetrics.LABEL_TENANT_ID, tenantId,
 *     IntelligenceMetrics.LABEL_DECISION_SOURCE, source.name,
 *     IntelligenceMetrics.LABEL_NODE_ID, nodeId
 * ).increment()
 *
 * // Prometheus direct
 * spice_intelligence_decision_total{tenant_id="stayfolio",decision_source="EMBEDDING",node_id="route-1"} 42
 * ```
 *
 * @since 1.1.0
 */
object IntelligenceMetrics {

    // ═══════════════════════════════════════════════════════
    // 1. DECISION METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Counter: Total intelligence decisions made.
     * Labels: tenant_id, decision_source, node_id
     */
    const val DECISION_TOTAL = "spice_intelligence_decision_total"

    /**
     * Histogram: Decision confidence distribution.
     * Labels: tenant_id, decision_source
     */
    const val DECISION_CONFIDENCE = "spice_intelligence_decision_confidence"

    /**
     * Timer: Decision latency.
     * Labels: tenant_id, decision_source
     */
    const val DECISION_LATENCY_SECONDS = "spice_intelligence_decision_latency_seconds"

    // ═══════════════════════════════════════════════════════
    // 2. AMBIGUITY METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Counter: Ambiguity detection results.
     * Labels: tenant_id, result (SINGLE|AMBIGUOUS|NONE)
     */
    const val AMBIGUITY_TOTAL = "spice_intelligence_ambiguity_total"

    /**
     * Counter: Candidate count distribution.
     * Labels: tenant_id, candidate_type
     */
    const val CANDIDATES_COUNT = "spice_intelligence_candidates_count"

    // ═══════════════════════════════════════════════════════
    // 3. CLARIFICATION METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Counter: Clarification requests made.
     * Labels: tenant_id, clarification_type, attempt_number
     */
    const val CLARIFICATION_REQUESTS_TOTAL = "spice_intelligence_clarification_requests_total"

    /**
     * Counter: Clarification results.
     * Labels: tenant_id, clarification_type, result (SUCCESS|STILL_AMBIGUOUS|USER_FRUSTRATED|TIMEOUT)
     */
    const val CLARIFICATION_RESULTS_TOTAL = "spice_intelligence_clarification_results_total"

    /**
     * Histogram: Clarification attempts per session.
     * Labels: tenant_id
     */
    const val CLARIFICATION_ATTEMPTS_HISTOGRAM = "spice_intelligence_clarification_attempts"

    // ═══════════════════════════════════════════════════════
    // 4. FALLBACK METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Counter: Fallback occurrences.
     * Labels: tenant_id, from_source, to_source
     */
    const val FALLBACK_TOTAL = "spice_intelligence_fallback_total"

    /**
     * Counter: Error recovery results.
     * Labels: tenant_id, error_type, recovery_action
     */
    const val ERROR_RECOVERY_TOTAL = "spice_intelligence_error_recovery_total"

    // ═══════════════════════════════════════════════════════
    // 5. LLM METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Counter: LLM calls.
     * Labels: tenant_id, llm_tier (NANO|BIG), reason
     */
    const val LLM_CALLS_TOTAL = "spice_intelligence_llm_calls_total"

    /**
     * Timer: LLM call latency.
     * Labels: tenant_id, llm_tier
     */
    const val LLM_LATENCY_SECONDS = "spice_intelligence_llm_latency_seconds"

    /**
     * Counter: LLM tokens used.
     * Labels: tenant_id, llm_tier, token_type (input|output)
     */
    const val LLM_TOKENS_TOTAL = "spice_intelligence_llm_tokens_total"

    // ═══════════════════════════════════════════════════════
    // 6. EMBEDDING METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Timer: Embedding call latency.
     * Labels: tenant_id, model
     */
    const val EMBEDDING_LATENCY_SECONDS = "spice_intelligence_embedding_latency_seconds"

    /**
     * Gauge: Semantic match score.
     * Labels: tenant_id, model
     */
    const val SEMANTIC_MATCH_SCORE = "spice_intelligence_semantic_match_score"

    /**
     * Counter: Embedding cache hits/misses.
     * Labels: tenant_id, cache_result (hit|miss)
     */
    const val EMBEDDING_CACHE_TOTAL = "spice_intelligence_embedding_cache_total"

    // ═══════════════════════════════════════════════════════
    // 7. LOOP GUARD METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Counter: LoopGuard triggers.
     * Labels: tenant_id, reason, action
     */
    const val LOOP_GUARD_TRIGGERS_TOTAL = "spice_intelligence_loop_guard_triggers_total"

    /**
     * Counter: Frustration signals detected.
     * Labels: tenant_id
     */
    const val FRUSTRATION_DETECTED_TOTAL = "spice_intelligence_frustration_detected_total"

    // ═══════════════════════════════════════════════════════
    // 8. PIPELINE METRICS
    // ═══════════════════════════════════════════════════════

    /**
     * Counter: Pipeline results.
     * Labels: tenant_id, workflow_id, result (SUCCESS|FAILED|TIMEOUT)
     */
    const val PIPELINE_RESULTS_TOTAL = "spice_intelligence_pipeline_results_total"

    /**
     * Timer: End-to-end pipeline latency.
     * Labels: tenant_id, workflow_id
     */
    const val PIPELINE_LATENCY_SECONDS = "spice_intelligence_pipeline_latency_seconds"

    // ═══════════════════════════════════════════════════════
    // LABEL KEYS
    // ═══════════════════════════════════════════════════════

    /** Tenant identifier */
    const val LABEL_TENANT_ID = "tenant_id"

    /** Decision source (SLM|EMBEDDING|NANO|FALLBACK|HITL|REANALYSIS) */
    const val LABEL_DECISION_SOURCE = "decision_source"

    /** Node identifier */
    const val LABEL_NODE_ID = "node_id"

    /** Clarification type */
    const val LABEL_CLARIFICATION_TYPE = "clarification_type"

    /** Attempt number */
    const val LABEL_ATTEMPT_NUMBER = "attempt_number"

    /** Result status */
    const val LABEL_RESULT = "result"

    /** Source of fallback (from) */
    const val LABEL_FROM_SOURCE = "from_source"

    /** Target of fallback (to) */
    const val LABEL_TO_SOURCE = "to_source"

    /** Error type */
    const val LABEL_ERROR_TYPE = "error_type"

    /** Recovery action taken */
    const val LABEL_RECOVERY_ACTION = "recovery_action"

    /** LLM tier (NANO|BIG) */
    const val LABEL_LLM_TIER = "llm_tier"

    /** Reason for action */
    const val LABEL_REASON = "reason"

    /** Model name */
    const val LABEL_MODEL = "model"

    /** Cache result (hit|miss) */
    const val LABEL_CACHE_RESULT = "cache_result"

    /** Token type (input|output) */
    const val LABEL_TOKEN_TYPE = "token_type"

    /** LoopGuard reason */
    const val LABEL_LOOP_GUARD_REASON = "loop_guard_reason"

    /** LoopGuard action */
    const val LABEL_LOOP_GUARD_ACTION = "loop_guard_action"

    /** Workflow identifier */
    const val LABEL_WORKFLOW_ID = "workflow_id"

    /** Candidate type */
    const val LABEL_CANDIDATE_TYPE = "candidate_type"

    /** Confidence bucket */
    const val LABEL_CONFIDENCE_BUCKET = "confidence_bucket"

    // ═══════════════════════════════════════════════════════
    // CONFIDENCE BUCKETS
    // ═══════════════════════════════════════════════════════

    /** High confidence (>= 0.85) */
    const val CONFIDENCE_HIGH = "high"

    /** Medium confidence (>= 0.65, < 0.85) */
    const val CONFIDENCE_MEDIUM = "medium"

    /** Low confidence (>= 0.5, < 0.65) */
    const val CONFIDENCE_LOW = "low"

    /** Very low confidence (< 0.5) */
    const val CONFIDENCE_VERY_LOW = "very_low"

    /**
     * Get confidence bucket for a score.
     *
     * Uses Intelligence Layer thresholds:
     * - High: >= 0.85 (EXACT match)
     * - Medium: >= 0.65 (FUZZY match)
     * - Low: >= 0.5 (borderline)
     * - Very Low: < 0.5 (likely wrong)
     *
     * @param score Confidence score (0.0-1.0)
     * @return Bucket name: "high", "medium", "low", or "very_low"
     */
    fun confidenceBucket(score: Double): String = when {
        score >= 0.85 -> CONFIDENCE_HIGH
        score >= 0.65 -> CONFIDENCE_MEDIUM
        score >= 0.50 -> CONFIDENCE_LOW
        else -> CONFIDENCE_VERY_LOW
    }

    // ═══════════════════════════════════════════════════════
    // LLM TIERS
    // ═══════════════════════════════════════════════════════

    /** Nano LLM (GPT-4o-mini, Claude Haiku 급) */
    const val LLM_TIER_NANO = "NANO"

    /** Big LLM (GPT-4o, Claude Sonnet 급) */
    const val LLM_TIER_BIG = "BIG"

    // ═══════════════════════════════════════════════════════
    // CACHE RESULT VALUES
    // ═══════════════════════════════════════════════════════

    const val CACHE_HIT = "hit"
    const val CACHE_MISS = "miss"

    // ═══════════════════════════════════════════════════════
    // PIPELINE RESULT VALUES
    // ═══════════════════════════════════════════════════════

    const val RESULT_SUCCESS = "SUCCESS"
    const val RESULT_FAILED = "FAILED"
    const val RESULT_TIMEOUT = "TIMEOUT"

    // ═══════════════════════════════════════════════════════
    // LOG FIELD NAMES
    // ═══════════════════════════════════════════════════════

    /**
     * Standard log field names for structured logging.
     */
    object LogFields {
        const val SESSION_ID = "intelligence.session_id"
        const val DECISION_TYPE = "intelligence.decision_type"
        const val DECISION_SOURCE = "intelligence.decision_source"
        const val CONFIDENCE = "intelligence.confidence"
        const val LATENCY_MS = "intelligence.latency_ms"
        const val REASONING = "intelligence.reasoning"
        const val SELECTED_OPTION = "intelligence.selected_option"
        const val CANDIDATES_COUNT = "intelligence.candidates_count"
        const val CLARIFICATION_TYPE = "intelligence.clarification_type"
        const val CLARIFICATION_ATTEMPT = "intelligence.clarification_attempt"
        const val FALLBACK_FROM = "intelligence.fallback_from"
        const val FALLBACK_TO = "intelligence.fallback_to"
        const val LOOP_GUARD_REASON = "intelligence.loop_guard_reason"
        const val LOOP_GUARD_ACTION = "intelligence.loop_guard_action"
        const val ERROR_TYPE = "intelligence.error_type"
        const val ERROR_MESSAGE = "intelligence.error_message"
        const val RECOVERY_ACTION = "intelligence.recovery_action"
    }
}
