package io.github.noailabs.spice.springboot.ai.intelligence

import io.github.noailabs.spice.intelligence.*
import io.github.noailabs.spice.intelligence.impl.*
import io.github.noailabs.spice.intelligence.spi.*
import io.github.noailabs.spice.routing.spi.SemanticMatcher
import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Intelligence Layer Spring Boot AutoConfiguration
 *
 * ## 활성화 조건
 * - `spice.intelligence.enabled=true` (기본값: true)
 *
 * ## 제공하는 Bean
 * - IntelligenceConfig: 설정
 * - IntelligenceCache: L1 캐시 (Caffeine)
 * - NanoValidator: Spring AI 기반 (ChatModel 필요)
 * - OneShotReasoner: Spring AI 기반 (ChatModel 필요)
 * - PolicyStore: InMemory (기본값)
 * - IntelligencePipeline: 파이프라인 오케스트레이터
 *
 * ## 설정 예시 (application.yml)
 * ```yaml
 * spice:
 *   intelligence:
 *     enabled: true
 *     exact-threshold: 0.85
 *     fuzzy-threshold: 0.65
 *     gap-threshold: 0.15
 *     domain-relevance-threshold: 0.70
 *     cache:
 *       enabled: true
 *       l1-ttl: 30m
 *       l1-max-size: 1000
 *     feature-flags:
 *       enable-nano-layer: true
 *       enable-big-layer: true
 *       rollout-percentage: 100
 * ```
 *
 * @since 2.0.0
 */
@Configuration
@EnableConfigurationProperties(IntelligenceProperties::class)
@ConditionalOnProperty(
    prefix = "spice.intelligence",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class IntelligenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun intelligenceConfig(properties: IntelligenceProperties): IntelligenceConfig {
        return IntelligenceConfig(
            exactThreshold = properties.exactThreshold,
            fuzzyThreshold = properties.fuzzyThreshold,
            gapThreshold = properties.gapThreshold,
            domainRelevanceThreshold = properties.domainRelevanceThreshold,
            cacheConfig = IntelligenceCacheConfig(
                enabled = properties.cache.enabled,
                l1Ttl = properties.cache.l1Ttl.minutes,
                l1MaxSize = properties.cache.l1MaxSize,
                cacheAmbiguous = properties.cache.cacheAmbiguous,
                ambiguousTtl = properties.cache.ambiguousTtl.minutes
            ),
            policyTopK = properties.policyTopK,
            policyRagTimeoutMs = properties.policyRagTimeoutMs,
            policyRagNanoTimeoutMs = properties.policyRagNanoTimeoutMs,
            fallbackConfig = FallbackConfig(
                confidenceMultiplier = properties.fallback.confidenceMultiplier,
                ambiguousThreshold = properties.fallback.ambiguousThreshold
            ),
            maxClarifyAttempts = properties.maxClarifyAttempts,
            loopGuardEnabled = properties.loopGuardEnabled,
            metricsEnabled = properties.metricsEnabled,
            featureFlags = IntelligenceFeatureFlags(
                enabled = properties.featureFlags.enabled,
                enableNanoLayer = properties.featureFlags.enableNanoLayer,
                enableBigLayer = properties.featureFlags.enableBigLayer,
                enablePolicyRag = properties.featureFlags.enablePolicyRag,
                rolloutPercentage = properties.featureFlags.rolloutPercentage,
                tenantWhitelist = properties.featureFlags.tenantWhitelist?.toSet(),
                tenantBlacklist = properties.featureFlags.tenantBlacklist.toSet()
            )
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun intelligenceCache(config: IntelligenceConfig): IntelligenceCache {
        return CaffeineIntelligenceCache(config.cacheConfig)
    }

    @Bean
    @ConditionalOnMissingBean
    fun domainRelevanceCalculator(): DomainRelevanceCalculator {
        return InMemoryDomainRelevanceCalculator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun policyStore(): PolicyStore {
        return InMemoryPolicyStore.withSamplePolicies()
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatModel::class)
    @ConditionalOnProperty(
        prefix = "spice.intelligence.feature-flags",
        name = ["enable-nano-layer"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun nanoValidator(
        chatModel: ChatModel,
        properties: IntelligenceProperties
    ): NanoValidator {
        return SpringAINanoValidator.from(chatModel, properties.nano.modelId)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatModel::class)
    @ConditionalOnProperty(
        prefix = "spice.intelligence.feature-flags",
        name = ["enable-big-layer"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun oneShotReasoner(
        chatModel: ChatModel,
        properties: IntelligenceProperties
    ): OneShotReasoner {
        return SpringAIOneShotReasoner.from(chatModel, properties.big.modelId)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SemanticMatcher::class)
    fun intelligencePipeline(
        semanticMatcher: SemanticMatcher,
        domainRelevanceCalculator: DomainRelevanceCalculator,
        nanoValidator: NanoValidator?,
        oneShotReasoner: OneShotReasoner?,
        policyStore: PolicyStore,
        cache: IntelligenceCache,
        config: IntelligenceConfig,
        metricsRecorder: PipelineMetricsRecorder?
    ): IntelligencePipeline {
        return IntelligencePipeline(
            semanticMatcher = semanticMatcher,
            domainRelevanceCalculator = domainRelevanceCalculator,
            nanoValidator = nanoValidator,
            oneShotReasoner = oneShotReasoner,
            policyStore = policyStore,
            cache = cache,
            config = config,
            metricsRecorder = metricsRecorder ?: PipelineMetricsRecorder.NO_OP
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun pipelineMetricsRecorder(): PipelineMetricsRecorder {
        // 기본값: NoOp (Micrometer 연동 시 별도 구현 제공)
        return PipelineMetricsRecorder.NO_OP
    }
}

/**
 * Intelligence Layer 설정 Properties
 */
@ConfigurationProperties(prefix = "spice.intelligence")
data class IntelligenceProperties(
    val enabled: Boolean = true,
    val exactThreshold: Double = 0.85,
    val fuzzyThreshold: Double = 0.65,
    val gapThreshold: Double = 0.15,
    val domainRelevanceThreshold: Double = 0.70,
    val policyTopK: Int = 3,
    val policyRagTimeoutMs: Long = 2000,
    val policyRagNanoTimeoutMs: Long = 500,
    val maxClarifyAttempts: Int = 3,
    val loopGuardEnabled: Boolean = true,
    val metricsEnabled: Boolean = true,
    val cache: CacheProperties = CacheProperties(),
    val fallback: FallbackProperties = FallbackProperties(),
    val featureFlags: FeatureFlagProperties = FeatureFlagProperties(),
    val nano: NanoProperties = NanoProperties(),
    val big: BigProperties = BigProperties()
)

data class CacheProperties(
    val enabled: Boolean = true,
    val l1Ttl: Long = 30,  // minutes
    val l1MaxSize: Int = 1000,
    val cacheAmbiguous: Boolean = true,
    val ambiguousTtl: Long = 2  // minutes
)

data class FallbackProperties(
    val confidenceMultiplier: Double = 0.8,
    val ambiguousThreshold: Double = 0.65
)

data class FeatureFlagProperties(
    val enabled: Boolean = true,
    val enableNanoLayer: Boolean = true,
    val enableBigLayer: Boolean = true,
    val enablePolicyRag: Boolean = true,
    val rolloutPercentage: Int = 100,
    val tenantWhitelist: List<String>? = null,
    val tenantBlacklist: List<String> = emptyList()
)

data class NanoProperties(
    val modelId: String = "gpt-4o-mini"
)

data class BigProperties(
    val modelId: String = "gpt-4o"
)
