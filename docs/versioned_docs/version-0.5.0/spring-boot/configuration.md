# Spring Boot Configuration

Configure Spice via application properties.

## application.yml

```yaml
spice:
  enabled: true
  openai:
    enabled: true
    apiKey: ${OPENAI_API_KEY}
    model: gpt-4
  anthropic:
    enabled: true
    apiKey: ${ANTHROPIC_API_KEY}
```
