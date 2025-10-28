# MCP Integration

Model Context Protocol integration.

## External Tools

```kotlin
val externalTool = externalTool("weather") {
    name = "weather-api"
    endpoint = "https://api.weather.com"
    apiKey = "your-key"
    timeout = 5000
}
```
