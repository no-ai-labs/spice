# Message Transformer Hooks

This package exposes hook points that run around graph execution. A transformer
implements `MessageTransformer` and may override any of:

- `beforeExecution`
- `beforeNode`
- `afterNode`
- `afterExecution`

Transformers are optional `@Bean`s. If multiple are present they run in the
order provided by Spring (e.g. `@Order`). Each hook receives the current
`Graph`/`SpiceMessage` and must return `SpiceResult<SpiceMessage>`.

See the Kotlin source files in this directory for the exact contracts.
