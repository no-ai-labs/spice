package io.github.noailabs.spice.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.semconv.ResourceAttributes
import java.util.concurrent.TimeUnit

/**
 * 📊 OpenTelemetry Configuration for Spice Framework
 *
 * Provides centralized configuration and initialization for:
 * - Distributed Tracing
 * - Metrics Collection
 * - Context Propagation
 */
object ObservabilityConfig {

    private var openTelemetry: OpenTelemetry? = null
    private var isInitialized = false

    /**
     * Configuration for OpenTelemetry
     */
    data class Config(
        val serviceName: String = "spice-framework",
        val serviceVersion: String = "0.1.2",
        val otlpEndpoint: String = "http://localhost:4317",
        val samplingRatio: Double = 1.0,  // 1.0 = 100% sampling
        val enableTracing: Boolean = true,
        val enableMetrics: Boolean = true,
        val exportIntervalMillis: Long = 30000,  // 30 seconds
        val environment: String = "development",
        val attributes: Map<String, String> = emptyMap()
    )

    /**
     * Initialize OpenTelemetry with given configuration
     */
    fun initialize(config: Config = Config()): OpenTelemetry {
        if (isInitialized) {
            return openTelemetry!!
        }

        val resource = Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, config.serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, config.serviceVersion)
                        .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, config.environment)
                        .also { builder ->
                            config.attributes.forEach { (key, value) ->
                                builder.put(key, value)
                            }
                        }
                        .build()
                )
            )

        val openTelemetryBuilder = OpenTelemetrySdk.builder()

        // Configure Tracing
        if (config.enableTracing) {
            val spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(config.otlpEndpoint)
                .setTimeout(10, TimeUnit.SECONDS)
                .build()

            val tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setSampler(
                    if (config.samplingRatio >= 1.0) Sampler.alwaysOn()
                    else Sampler.traceIdRatioBased(config.samplingRatio)
                )
                .build()

            openTelemetryBuilder.setTracerProvider(tracerProvider)
        }

        // Configure Metrics
        if (config.enableMetrics) {
            val metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(config.otlpEndpoint)
                .setTimeout(10, TimeUnit.SECONDS)
                .build()

            val metricReader = PeriodicMetricReader.builder(metricExporter)
                .setInterval(config.exportIntervalMillis, TimeUnit.MILLISECONDS)
                .build()

            val meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(metricReader)
                .build()

            openTelemetryBuilder.setMeterProvider(meterProvider)
        }

        // Configure Context Propagation
        openTelemetryBuilder.setPropagators(
            ContextPropagators.create(W3CTraceContextPropagator.getInstance())
        )

        openTelemetry = openTelemetryBuilder.buildAndRegisterGlobal()
        isInitialized = true

        return openTelemetry!!
    }

    /**
     * Get OpenTelemetry instance (initializes with defaults if not already initialized)
     */
    fun get(): OpenTelemetry {
        if (!isInitialized) {
            return initialize()
        }
        return openTelemetry!!
    }

    /**
     * Get a tracer for the given instrumentation scope
     */
    fun getTracer(instrumentationScopeName: String = "io.github.noailabs.spice"): Tracer {
        return get().getTracer(instrumentationScopeName)
    }

    /**
     * Check if observability is enabled
     */
    fun isEnabled(): Boolean = isInitialized

    /**
     * Shutdown OpenTelemetry (for graceful cleanup)
     */
    fun shutdown() {
        if (openTelemetry is OpenTelemetrySdk) {
            (openTelemetry as OpenTelemetrySdk).close()
        }
        isInitialized = false
        openTelemetry = null
    }
}
