# Observability Setup

> 📝 **Coming Soon**: Guide to setting up observability for Spice Framework.

## Preview

Configure logging, metrics, and tracing:

```kotlin
val agent = buildAgent {
    id = "my-agent"

    observability {
        logging {
            level = LogLevel.INFO
        }

        metrics {
            enabled = true
            exporter = PrometheusExporter()
        }

        tracing {
            enabled = true
            exporter = JaegerExporter()
        }
    }
}
```

## See Also

- [Observability Overview](./overview.md)
