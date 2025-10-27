# SPARQL Extensions Guide

Comprehensive guide to using SPARQL features in Spice Framework including Named Graphs auto-injection and Template Repository.

## Overview

Spice Framework provides powerful SPARQL integration features:

- **Named Graphs Extension**: Automatic context-aware Named Graph injection
- **SPARQL Template Repository**: Centralized template management and reuse
- **Template DSL**: Declarative template definition with parameters and validation
- **Handlebars Integration**: Dynamic query templating with full Handlebars support

These features work together to enable maintainable, multi-tenant SPARQL queries with automatic context propagation.

## Quick Start

### Basic SPARQL Tool with Named Graphs

```kotlin
// 1. Register Named Graphs extension
val extension = namedGraphsExtension {
    baseUri = "http://example.com/graphs"
    tenantWithShared()  // Tenant graph + shared graph
}

ContextExtensionRegistry.register(extension)

// 2. Create context-aware SPARQL tool
val getTool = contextAwareTool("get_product") {
    description = "Get product by SKU"

    param("sku", "string", "Product SKU", required = true)

    sparql {
        endpoint = "http://localhost:3030/catalog/query"
        template = """
            SELECT ?name ?price
            {{namedGraphsClause}}
            WHERE {
                ?product kai:id "{{sku}}" .
                ?product kai:name ?name .
                ?product kai:price ?price .
            }
        """
        useContextGraphs()  // Automatically use graphs from context
    }
}

// 3. Execute with context
withAgentContext("tenantId" to "ACME") {
    val result = getTool.execute(mapOf("sku" to "SKU-123"))
    println(result)
}
```

### Using Template Repository

```kotlin
// 1. Register template
sparqlTemplate("product_lookup") {
    description = "Look up product by SKU"

    param("sku", "string", "Product SKU", required = true)
    param("spec_keys", "array", "Specification keys", required = false)

    template("""
        SELECT ?spec ?value
        {{namedGraphsClause}}
        WHERE {
            ?product kai:id "{{sku}}" .
            ?product ?spec ?value .
            {{#if spec_keys}}
            FILTER(?spec IN ({{#each spec_keys}}<{{this}}>,{{/each}}))
            {{/if}}
        }
    """)

    example(
        description = "Get all specs for SKU-123",
        params = mapOf("sku" to "SKU-123")
    )
}

// 2. Use registered template
val tool = contextAwareTool("get_product_specs") {
    description = "Retrieve product specifications"
    param("sku", "string", "Product SKU", required = true)

    sparql {
        endpoint = "http://localhost:3030/catalog/query"
        templateRef = "product_lookup"  // Reference registered template
        useContextGraphs()
    }
}
```

## Named Graphs Extension

### Overview

The Named Graphs Extension automatically enriches `AgentContext` with tenant/user/session-specific RDF Named Graphs. This enables multi-tenant data isolation and hierarchical data access patterns.

### Built-in Strategies

#### 1. Tenant-Only Strategy

Single graph per tenant:

```kotlin
val extension = namedGraphsExtension {
    baseUri = "http://example.com/graphs"
    tenantOnly()
}

// For tenantId = "ACME":
// -> http://example.com/graphs/tenant/ACME
```

#### 2. Tenant + Shared Strategy

Tenant graph plus shared reference data:

```kotlin
val extension = namedGraphsExtension {
    baseUri = "http://example.com/graphs"
    tenantWithShared()
}

// For tenantId = "ACME":
// -> http://example.com/graphs/tenant/ACME
// -> http://example.com/graphs/shared
```

#### 3. Hierarchical Strategy

User → Tenant → Shared hierarchy:

```kotlin
val extension = namedGraphsExtension {
    baseUri = "http://example.com/graphs"
    hierarchical()
}

// For tenantId = "ACME", userId = "user-123":
// -> http://example.com/graphs/user/user-123
// -> http://example.com/graphs/tenant/ACME
// -> http://example.com/graphs/shared
```

#### 4. Type-Partitioned Strategy

Separate graphs by entity type:

```kotlin
val extension = namedGraphsExtension {
    baseUri = "http://kaibrain.com/graphs"
    typePartitioned(listOf("policy", "product", "order"))
}

// For tenantId = "ACME":
// -> http://kaibrain.com/graphs/tenant/ACME/policy
// -> http://kaibrain.com/graphs/tenant/ACME/product
// -> http://kaibrain.com/graphs/tenant/ACME/order
```

#### 5. Session-Based Strategy

Session-specific temporary data:

```kotlin
val extension = namedGraphsExtension {
    baseUri = "http://example.com/graphs"
    sessionBased()
}

// For tenantId = "ACME", sessionId = "session-456":
// -> http://example.com/graphs/session/session-456
// -> http://example.com/graphs/tenant/ACME
```

### Custom Strategies

Define your own graph selection logic:

```kotlin
val extension = namedGraphsExtension {
    baseUri = "http://kaibrain.com/graphs"

    strategy { context ->
        buildList {
            // Session graph if session exists
            context.sessionId?.let {
                add(sessionGraph(it))
            }

            // User graph if user exists
            context.userId?.let {
                add(userGraph(it))
            }

            // Tenant graph (required)
            context.tenantId?.let {
                add(tenantGraph(it))
            }

            // Always include shared
            add(shared())
        }
    }
}
```

### Fallback Strategies

Provide fallback for error resilience:

```kotlin
val primary: suspend (AgentContext) -> List<String> = { context ->
    // Try to fetch from external service
    fetchGraphsFromService(context.tenantId)
}

val fallback = NamedGraphsStrategies.tenantOnly("http://fallback.com/graphs")

val extension = NamedGraphsExtension(
    NamedGraphsStrategies.withFallback(primary, fallback)
)
```

### Graph Builder Helpers

Use convenience functions for building graph URIs:

```kotlin
val extension = namedGraphsExtension {
    baseUri = "http://example.com/graphs"

    strategy { context ->
        listOf(
            tenantGraph(context.tenantId!!),        // .../tenant/ACME
            userGraph(context.userId!!),            // .../user/user-123
            sessionGraph(context.sessionId!!),      // .../session/session-456
            typeGraph("policy"),                    // .../type/policy
            shared(),                               // .../shared
            compositeGraph("tenant", "ACME", "policy")  // .../tenant/ACME/policy
        )
    }
}
```

### Accessing Named Graphs

In your tools, access graphs from context:

```kotlin
val tool = contextAwareTool("my_tool") {
    execute { params, context ->
        val graphs = context.getNamedGraphs()
        println("Using graphs: $graphs")

        // Manual query building
        val namedGraphsClause = buildNamedGraphsClause(graphs)
        val query = """
            SELECT ?s ?p ?o
            $namedGraphsClause
            WHERE {
                ?s ?p ?o .
            }
        """

        // Execute query...
    }
}
```

Or use automatic injection:

```kotlin
sparql {
    endpoint = "..."
    template = "..."
    useContextGraphs()  // Automatic injection
}
```

## SPARQL Template Repository

### Overview

The Template Repository provides centralized management for SPARQL query templates, enabling:

- **Reusability**: Define once, use everywhere
- **Validation**: Parameter validation and type checking
- **Documentation**: Built-in parameter descriptions and examples
- **Composition**: Partial templates for common patterns
- **Testing**: Template examples serve as test cases

### Registering Templates

#### Inline Templates

Simple string registration:

```kotlin
SparqlTemplateRepository.register("get_all", """
    SELECT ?s ?p ?o
    WHERE {
        ?s ?p ?o
    }
    LIMIT 10
""")
```

#### Template Builder DSL

Rich templates with metadata:

```kotlin
sparqlTemplate("product_search") {
    description = "Search products by criteria"

    // Define parameters
    param("name_pattern", "string", "Product name pattern", required = false)
    param("min_price", "number", "Minimum price", required = false)
    param("max_price", "number", "Maximum price", required = false)
    param("limit", "integer", "Result limit", required = false, defaultValue = 100)

    // Template content
    template("""
        SELECT DISTINCT ?product ?name ?price
        {{namedGraphsClause}}
        WHERE {
            ?product rdf:type kai:Product .
            ?product kai:name ?name .
            ?product kai:price ?price .

            {{#if name_pattern}}
            FILTER(CONTAINS(LCASE(?name), LCASE("{{name_pattern}}")))
            {{/if}}

            {{#if min_price}}
            FILTER(?price >= {{min_price}})
            {{/if}}

            {{#if max_price}}
            FILTER(?price <= {{max_price}})
            {{/if}}
        }
        ORDER BY ?name
        {{#if limit}}
        LIMIT {{limit}}
        {{/if}}
    """)

    // Usage examples
    example(
        description = "Search for 'laptop' products",
        params = mapOf("name_pattern" to "laptop", "limit" to 10)
    )

    example(
        description = "Products in price range",
        params = mapOf("min_price" to 100, "max_price" to 500)
    )
}
```

#### File-Based Templates

Load from files:

```kotlin
// Single file
SparqlTemplateRepository.registerFile(
    "my_query",
    File("templates/my_query.sparql")
)

// Directory (auto-discover)
SparqlTemplateRepository.loadFromDirectory(
    File("templates/"),
    extension = ".sparql"
)
```

#### Classpath Resources

Load from resources:

```kotlin
SparqlTemplateRepository.registerResource(
    "built_in_query",
    "sparql/queries/built_in_query.sparql"
)
```

### Using Templates

#### Direct Rendering

```kotlin
val template = SparqlTemplateRepository.require("product_search")
val engine = HandlebarsTemplateEngine()

val params = mapOf(
    "name_pattern" to "laptop",
    "min_price" to 100,
    "namedGraphsClause" to "FROM NAMED <http://example.com/graphs/tenant/ACME>"
)

val query = template.render(params, engine)
println(query)
```

#### Template References in Tools

```kotlin
val tool = contextAwareTool("search_products") {
    description = "Search for products"

    param("name_pattern", "string", "Product name pattern", required = false)
    param("min_price", "number", "Minimum price", required = false)

    sparql {
        endpoint = "http://localhost:3030/catalog/query"
        templateRef = "product_search"  // Reference by name
        useContextGraphs()
    }
}
```

### Common Templates

Built-in templates for standard SPARQL operations:

```kotlin
// Register all common templates
SparqlTemplateRepository.registerCommonTemplates()

// Available templates:
// - sparql:select     - Generic SELECT query
// - sparql:ask        - ASK query (boolean)
// - sparql:construct  - CONSTRUCT query
// - sparql:describe   - DESCRIBE query
// - sparql:insert     - INSERT DATA update
// - sparql:delete     - DELETE WHERE update
```

#### SELECT Template

```kotlin
val params = mapOf(
    "distinct" to true,
    "variables" to "?product ?name ?price",
    "whereClause" to "?product kai:name ?name . ?product kai:price ?price .",
    "filters" to "FILTER(?price > 100)",
    "orderBy" to "?name",
    "limit" to 10,
    "offset" to 5
)

val query = engine.render(CommonTemplates.select, params)
```

#### ASK Template

```kotlin
val params = mapOf(
    "whereClause" to "?s rdf:type foaf:Person ."
)

val query = engine.render(CommonTemplates.ask, params)
```

#### INSERT Template

```kotlin
val params = mapOf(
    "graph" to "http://example.com/graphs/tenant/ACME",
    "triples" to "<http://ex/s> <http://ex/p> <http://ex/o> ."
)

val query = engine.render(CommonTemplates.insert, params)
```

### Partial Templates

Reusable template fragments:

```kotlin
// Register partials
SparqlTemplateRepository.registerPartial("age_filter", """
    FILTER(?age > {{minAge}})
    {{#if maxAge}}
    FILTER(?age < {{maxAge}})
    {{/if}}
""")

// Use in templates
val template = """
    SELECT ?person ?age
    WHERE {
        ?person foaf:age ?age .
        {{> age_filter}}
    }
"""
```

Common partials are registered automatically:

- `filters` - Multiple FILTER clauses
- `optional` - OPTIONAL block
- `union` - UNION patterns

### Template Validation

Templates support parameter validation:

```kotlin
val template = SparqlTemplateRepository.require("product_search")

// Validate parameters
val errors = template.validateParameters(mapOf(
    "name_pattern" to "laptop"
    // Missing required params will be reported
))

if (errors.isNotEmpty()) {
    println("Validation errors:")
    errors.forEach { println("  - $it") }
}
```

### Template Metadata

Access template information:

```kotlin
val template = SparqlTemplateRepository.require("product_search")

println("Name: ${template.name}")
println("Description: ${template.description}")
println("Source: ${template.source}")  // STRING, FILE, or RESOURCE
println("Source Path: ${template.sourcePath}")

// Parameters
template.parameters.forEach { param ->
    println("Param: ${param.name} (${param.type})")
    println("  Required: ${param.required}")
    println("  Default: ${param.defaultValue}")
    println("  Description: ${param.description}")
}

// Examples
template.examples.forEach { example ->
    println("Example: ${example.description}")
    println("  Params: ${example.parameters}")
    println("  Expected: ${example.expectedResult}")
}
```

## Integration Patterns

### Pattern 1: Simple Query Tool

Basic query with auto-injected graphs:

```kotlin
val tool = contextAwareTool("get_entities") {
    description = "Get entities by type"
    param("entity_type", "string", "RDF entity type", required = true)

    sparql {
        endpoint = "http://localhost:3030/data/query"
        template = """
            SELECT ?entity ?label
            {{namedGraphsClause}}
            WHERE {
                ?entity rdf:type <{{entity_type}}> .
                ?entity rdfs:label ?label .
            }
        """
        useContextGraphs()
    }
}
```

### Pattern 2: Template-Based Tool

Using registered templates:

```kotlin
// 1. Register template
sparqlTemplate("entity_lookup") {
    description = "Look up entity by ID"
    param("entity_id", "string", "Entity identifier", required = true)
    template("""
        SELECT ?p ?o
        {{namedGraphsClause}}
        WHERE {
            <{{entity_id}}> ?p ?o .
        }
    """)
}

// 2. Create tool
val tool = contextAwareTool("get_entity") {
    description = "Get entity details"
    param("entity_id", "string", "Entity ID", required = true)

    sparql {
        endpoint = "http://localhost:3030/data/query"
        templateRef = "entity_lookup"
        useContextGraphs()
    }
}
```

### Pattern 3: Custom Graph Provider

Override context graphs with custom logic:

```kotlin
val tool = contextAwareTool("get_policy") {
    description = "Get policy details"
    param("policy_id", "string", "Policy ID", required = true)

    sparql {
        endpoint = "http://localhost:3030/policies/query"
        template = "..."

        // Custom graph selection
        namedGraphs { context ->
            val graphs = mutableListOf<String>()

            // Add policy-specific graph
            graphs.add("http://example.com/graphs/policies")

            // Add tenant graph
            context.tenantId?.let {
                graphs.add("http://example.com/graphs/tenant/$it")
            }

            graphs
        }
    }
}
```

### Pattern 4: Result Transformation

Transform SPARQL results before returning:

```kotlin
val tool = contextAwareTool("get_product_evidence") {
    description = "Get product with evidence"
    param("sku", "string", "Product SKU", required = true)

    sparql {
        endpoint = "http://localhost:3030/catalog/query"
        template = "..."
        useContextGraphs()

        // Transform to Evidence JSON format
        transform { results ->
            buildEvidenceJson {
                results.forEach { binding ->
                    evidence {
                        statement = binding["name"] as String
                        citation = "RDF Graph: ${binding["graph"]}"
                        confidence = 1.0
                        metadata = mapOf(
                            "sku" to binding["sku"],
                            "source" to "SPARQL"
                        )
                    }
                }
            }
        }
    }
}
```

### Pattern 5: Combined with Caching

Cache SPARQL results for performance:

```kotlin
val tool = contextAwareTool("get_product") {
    description = "Get product (cached)"
    param("sku", "string", "Product SKU", required = true)

    sparql {
        endpoint = "http://localhost:3030/catalog/query"
        templateRef = "product_lookup"
        useContextGraphs()
    }

    // Cache results for 5 minutes
    cache {
        ttl = 300
        maxSize = 1000
        customKey { params, context ->
            "product:${context.tenantId}:${params["sku"]}"
        }
    }
}
```

### Pattern 6: Combined with Validation

Validate SPARQL results:

```kotlin
val tool = contextAwareTool("get_product") {
    description = "Get product with validation"
    param("sku", "string", "Product SKU", required = true)

    sparql {
        endpoint = "http://localhost:3030/catalog/query"
        templateRef = "product_lookup"
        useContextGraphs()
    }

    // Validate result structure
    validate {
        requireField("name", "Product must have a name")
        requireField("price", "Product must have a price")

        fieldType("price", FieldType.NUMBER)
        range("price", min = 0.0, max = 1000000.0)
    }
}
```

## Best Practices

### 1. Use Named Graphs Consistently

**Do:**
```kotlin
// Register extension once at startup
val extension = namedGraphsExtension {
    baseUri = "http://kaibrain.com/graphs"
    tenantWithShared()
}
ContextExtensionRegistry.register(extension)

// Use in all tools
sparql {
    endpoint = "..."
    template = "..."
    useContextGraphs()  // Consistent across all tools
}
```

**Don't:**
```kotlin
// Inconsistent graph selection per tool
sparql {
    namedGraphs { context ->
        listOf("http://example.com/graphs/tenant/${context.tenantId}")
    }
}
```

### 2. Register Templates Centrally

**Do:**
```kotlin
// In application initialization
object SparqlTemplates {
    fun registerAll() {
        SparqlTemplateRepository.registerCommonTemplates()

        sparqlTemplate("product_lookup") { ... }
        sparqlTemplate("order_lookup") { ... }
        sparqlTemplate("policy_lookup") { ... }
    }
}

// In main()
SparqlTemplates.registerAll()
```

**Don't:**
```kotlin
// Registering templates in multiple places
fun createProductTool() {
    sparqlTemplate("product_lookup") { ... }  // Registered here
    // ...
}

fun createOrderTool() {
    sparqlTemplate("product_lookup") { ... }  // Duplicate registration!
    // ...
}
```

### 3. Document Template Parameters

**Do:**
```kotlin
sparqlTemplate("product_search") {
    description = "Search products by multiple criteria"

    param("name_pattern", "string", "Case-insensitive name search", required = false)
    param("min_price", "number", "Minimum price in USD", required = false)
    param("limit", "integer", "Maximum results (default: 100)",
          required = false, defaultValue = 100)

    template("...")

    example(
        description = "Search for laptops under $1000",
        params = mapOf("name_pattern" to "laptop", "max_price" to 1000)
    )
}
```

**Don't:**
```kotlin
sparqlTemplate("product_search") {
    template("""
        SELECT ?product ?name ?price
        WHERE {
            ?product kai:name ?name .
            {{#if x}}FILTER(?price > {{x}}){{/if}}
        }
    """)
    // No parameter documentation!
}
```

### 4. Use Template Validation

**Do:**
```kotlin
val template = SparqlTemplateRepository.require("product_search")
val errors = template.validateParameters(params)

if (errors.isNotEmpty()) {
    throw IllegalArgumentException("Invalid parameters: ${errors.joinToString()}")
}

val query = template.render(params, engine)
```

**Don't:**
```kotlin
// Skip validation, get runtime errors
val query = template.render(params, engine)  // Boom! Missing required params
```

### 5. Handle Named Graphs Clause Properly

**Do:**
```kotlin
template = """
    SELECT ?s ?p ?o
    {{namedGraphsClause}}
    WHERE {
        ?s ?p ?o .
    }
"""
// namedGraphsClause is auto-populated by SPARQL DSL
```

**Don't:**
```kotlin
template = """
    SELECT ?s ?p ?o
    FROM NAMED <http://hardcoded/graph>
    WHERE {
        ?s ?p ?o .
    }
"""
// Hardcoded graphs break multi-tenancy!
```

### 6. Use Handlebars Features

**Do:**
```kotlin
template = """
    SELECT ?product ?name ?price
    WHERE {
        ?product rdf:type kai:Product .
        ?product kai:name ?name .
        ?product kai:price ?price .

        {{#if filters}}
        {{#each filters}}
        FILTER({{{this}}})
        {{/each}}
        {{/if}}

        {{#if categories}}
        VALUES ?category { {{#each categories}}<{{this}}> {{/each}} }
        ?product kai:category ?category .
        {{/if}}
    }
    {{#if orderBy}}
    ORDER BY {{orderBy}}
    {{/if}}
"""
```

**Don't:**
```kotlin
// Building queries with string concatenation
var query = "SELECT ?product WHERE { ?product rdf:type kai:Product ."
if (filters != null) {
    query += " FILTER($filters)"
}
query += " }"
// Error-prone and hard to maintain!
```

## Testing

### Testing Named Graphs Extension

```kotlin
@Test
fun `test named graphs extension`() = runTest {
    val extension = namedGraphsExtension {
        baseUri = "http://test.com/graphs"
        tenantWithShared()
    }

    val context = AgentContext.of("tenantId" to "TEST")
    val enriched = extension.enrich(context)

    val graphs = enriched.getNamedGraphs()
    assertNotNull(graphs)
    assertEquals(2, graphs.size)
    assertTrue(graphs.contains("http://test.com/graphs/tenant/TEST"))
    assertTrue(graphs.contains("http://test.com/graphs/shared"))
}
```

### Testing Templates

```kotlin
@Test
fun `test template rendering`() {
    sparqlTemplate("test_query") {
        param("name", required = true)
        template("""
            SELECT ?s WHERE {
                ?s foaf:name "{{name}}" .
            }
        """)
    }

    val template = SparqlTemplateRepository.require("test_query")
    val engine = HandlebarsTemplateEngine()

    val rendered = template.render(
        mapOf("name" to "Alice"),
        engine
    )

    assertTrue(rendered.contains("\"Alice\""))
}
```

### Testing Template Validation

```kotlin
@Test
fun `test template parameter validation`() {
    sparqlTemplate("test_query") {
        param("required_param", required = true)
        param("optional_param", required = false)
        template("SELECT * WHERE { }")
    }

    val template = SparqlTemplateRepository.require("test_query")

    // Valid
    val errors1 = template.validateParameters(mapOf("required_param" to "value"))
    assertTrue(errors1.isEmpty())

    // Invalid - missing required
    val errors2 = template.validateParameters(mapOf("optional_param" to "value"))
    assertEquals(1, errors2.size)
    assertTrue(errors2[0].contains("required_param"))
}
```

### Testing Tools with SPARQL

```kotlin
@Test
fun `test SPARQL tool execution`() = runTest {
    // Register extension
    val extension = namedGraphsExtension {
        baseUri = "http://test.com/graphs"
        tenantOnly()
    }
    ContextExtensionRegistry.register(extension)

    // Create tool
    val tool = contextAwareTool("test_tool") {
        param("id", "string", required = true)

        sparql {
            endpoint = "http://localhost:3030/test/query"
            template = """
                SELECT ?name
                {{namedGraphsClause}}
                WHERE {
                    <{{id}}> foaf:name ?name .
                }
            """
            useContextGraphs()
        }
    }

    // Execute with context
    withAgentContext("tenantId" to "TEST") {
        val result = tool.execute(mapOf("id" to "http://ex/person1"))
        assertTrue(result.isSuccess)
    }
}
```

## API Reference

### Named Graphs Extension

```kotlin
// Extension creation
fun namedGraphsExtension(block: NamedGraphsExtensionBuilder.() -> Unit): NamedGraphsExtension

class NamedGraphsExtensionBuilder {
    var baseUri: String
    fun tenantOnly()
    fun tenantWithShared()
    fun hierarchical()
    fun typePartitioned(types: List<String>)
    fun sessionBased()
    fun strategy(provider: suspend (AgentContext) -> List<String>)
}

// Strategies
object NamedGraphsStrategies {
    fun tenantOnly(baseUri: String): suspend (AgentContext) -> List<String>
    fun tenantWithShared(baseUri: String): suspend (AgentContext) -> List<String>
    fun hierarchical(baseUri: String): suspend (AgentContext) -> List<String>
    fun typePartitioned(baseUri: String, types: List<String>): suspend (AgentContext) -> List<String>
    fun sessionBased(baseUri: String): suspend (AgentContext) -> List<String>
    fun withFallback(
        primary: suspend (AgentContext) -> List<String>,
        fallback: suspend (AgentContext) -> List<String>
    ): suspend (AgentContext) -> List<String>
}

// Graph builders
object NamedGraphsBuilder {
    fun tenantGraph(baseUri: String, tenantId: String): String
    fun userGraph(baseUri: String, userId: String): String
    fun sessionGraph(baseUri: String, sessionId: String): String
    fun typeGraph(baseUri: String, type: String): String
    fun sharedGraph(baseUri: String): String
    fun compositeGraph(baseUri: String, vararg parts: String): String
}

// Context access
fun AgentContext.getNamedGraphs(): List<String>?
```

### Template Repository

```kotlin
// Registration
object SparqlTemplateRepository {
    fun register(name: String, template: String)
    fun register(name: String, builder: SparqlTemplateBuilder.() -> Unit)
    fun registerFile(name: String, file: File)
    fun registerResource(name: String, resourcePath: String)
    fun registerPartial(name: String, template: String)
    fun registerCommonTemplates()

    fun get(name: String): SparqlTemplate?
    fun require(name: String): SparqlTemplate
    fun getPartial(name: String): String?
    fun has(name: String): Boolean

    fun list(): List<String>
    fun listPartials(): List<String>
    fun clear()

    fun loadFromDirectory(dir: File, extension: String = ".sparql")
}

// Template DSL
fun sparqlTemplate(name: String, builder: SparqlTemplateBuilder.() -> Unit)

class SparqlTemplateBuilder(val name: String) {
    var description: String
    fun template(content: String)
    fun param(
        name: String,
        type: String = "string",
        description: String = "",
        required: Boolean = true,
        defaultValue: Any? = null
    )
    fun example(
        description: String,
        params: Map<String, Any>,
        expectedResult: String? = null
    )
}

// Template model
data class SparqlTemplate(
    val name: String,
    val content: String,
    val source: TemplateSource,
    val sourcePath: String? = null,
    val description: String = "",
    val parameters: List<TemplateParameter> = emptyList(),
    val examples: List<TemplateExample> = emptyList()
) {
    fun render(params: Map<String, Any>, engine: HandlebarsTemplateEngine): String
    fun validateParameters(params: Map<String, Any>): List<String>
}

enum class TemplateSource {
    STRING, FILE, RESOURCE
}

data class TemplateParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null
)

data class TemplateExample(
    val description: String,
    val parameters: Map<String, Any>,
    val expectedResult: String? = null
)

// Common templates
object CommonTemplates {
    val select: String
    val ask: String
    val construct: String
    val describe: String
    val insert: String
    val delete: String

    object Partials {
        val filters: String
        val optional: String
        val union: String
    }
}
```

### SPARQL DSL

```kotlin
// Tool extension
fun ContextAwareToolBuilder.sparql(init: SparqlConfigBlock.() -> Unit)

class SparqlConfigBlock {
    var endpoint: String
    var updateEndpoint: String?
    var template: String
    var templateFile: String
    var templateDir: File?
    var askMode: Boolean
    var updateMode: Boolean
    var timeout: Long
    var headers: Map<String, String>
    var useContextGraphs: Boolean

    fun namedGraphs(provider: (AgentContext) -> List<String>)
    fun useContextGraphs()
    fun transform(transformer: (List<Map<String, Any>>) -> Any)
    fun validate()
}

// Template reference
var SparqlConfigBlock.templateRef: String
```

## See Also

- [Context-Aware Tools Guide](../dsl-guide/context-aware-tools.md)
- [Tool Caching Guide](../performance/tool-caching.md)
- [Output Validation Guide](../dsl-guide/output-validation.md)
- [RDF4J Documentation](https://rdf4j.org/)
- [Handlebars Documentation](https://github.com/jknack/handlebars.java)

## Troubleshooting

### Named Graphs Not Applied

**Problem**: Queries execute but Named Graphs clause is empty.

**Solution**: Ensure extension is registered and context is enriched:
```kotlin
// 1. Register extension
ContextExtensionRegistry.register(extension)

// 2. Use withAgentContext
withAgentContext("tenantId" to "ACME") {
    tool.execute(params)
}

// 3. Verify in tool
sparql {
    useContextGraphs()  // Must be enabled
}
```

### Template Not Found

**Problem**: `IllegalArgumentException: Template not found: my_template`

**Solution**: Register template before use:
```kotlin
// Register at startup
sparqlTemplate("my_template") { ... }

// Then use
sparql {
    templateRef = "my_template"
}
```

### Template Rendering Errors

**Problem**: Handlebars syntax errors or missing variables.

**Solution**:
1. Use triple braces `{{{variable}}}` for SPARQL content
2. Ensure all parameters are provided
3. Use `{{#if}}` blocks for optional content

```kotlin
// Correct
template = """
    SELECT {{{variables}}}
    WHERE {
        {{{whereClause}}}
        {{#if filters}}
        {{{filters}}}
        {{/if}}
    }
"""

// Incorrect
template = """
    SELECT {{variables}}  <!-- Will escape ? characters! -->
    WHERE {
        {{whereClause}}
        {{{filters}}}  <!-- Will error if filters not provided -->
    }
"""
```

### Context Not Propagating

**Problem**: AgentContext is lost in tool execution.

**Solution**: Use `withAgentContext`, don't nest `runBlocking`:
```kotlin
// Correct
withAgentContext("tenantId" to "ACME") {
    tool.execute(params)
}

// Incorrect
withAgentContext("tenantId" to "ACME") {
    runBlocking {  // Creates new context!
        tool.execute(params)
    }
}
```
