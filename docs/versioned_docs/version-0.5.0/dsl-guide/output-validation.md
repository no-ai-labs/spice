# Output Validation DSL

Complete guide to enforcing output structure and quality with declarative validation rules, including type checking, range validation, pattern matching, and custom business logic.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Validation Rules](#validation-rules)
- [Field Types](#field-types)
- [Custom Validation](#custom-validation)
- [Usage Patterns](#usage-patterns)
- [Advanced Techniques](#advanced-techniques)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [API Reference](#api-reference)

## Overview

Output Validation DSL provides declarative, type-safe validation for tool outputs. It ensures data quality, prevents downstream errors, and makes output contracts explicit.

### Why Output Validation?

**Without Validation:**
```kotlin
// Tool returns inconsistent output
tool.execute(params)  // Sometimes: { "citations": [...] }
                      // Sometimes: { "citations": null }
                      // Sometimes: { } (missing citations!)

// Downstream code crashes
val citations = result["citations"] as List<String>  // NPE or ClassCastException!
```

**With Validation:**
```kotlin
// Tool output is guaranteed to match schema
validate {
    requireField("citations")
    fieldType("citations", FieldType.ARRAY)
}

// Downstream code is safe
val citations = result["citations"] as List<String>  // Always works!
```

### Key Features

| Feature | Description | Benefit |
|---------|-------------|---------|
| ✅ **Declarative** | Define rules in `validate {}` block | Easy to read and maintain |
| 🎯 **Type Safety** | Validate field types | Prevents type errors |
| 📏 **Range Checks** | Numeric bounds validation | Data integrity |
| 🔍 **Pattern Matching** | Regex validation | String format enforcement |
| 🔧 **Custom Rules** | Arbitrary validation logic | Business rule enforcement |
| 🌍 **Context-Aware** | Access AgentContext | Tenant-specific validation |
| ⚡ **Fail-Fast** | Stops at first error | Quick feedback |
| 📋 **Clear Errors** | Descriptive error messages | Easy debugging |

### When to Use Validation

✅ **Always Use Validation For:**
- Critical outputs (financial data, evidence, audit trails)
- LLM-generated content (structured extraction)
- External API responses (third-party data)
- User-provided data (form inputs, uploads)
- Inter-service communication (microservices)

❌ **Optional For:**
- Internal tool outputs (trusted code)
- Simple string/number returns
- Temporary/debugging outputs

## Quick Start

### 1. Basic Field Validation

Ensure required fields are present:

```kotlin
val evidenceTool = contextAwareTool("generate_evidence") {
    description = "Generate evidence with citations"

    // ✅ Define validation rules
    validate {
        requireField("citations", "Evidence must include citations")
        requireField("summary", "Evidence must include summary")
        requireField("confidence")
    }

    execute { params, context ->
        // Output must match validation schema
        mapOf(
            "citations" to listOf("source1", "source2"),
            "summary" to "Evidence summary",
            "confidence" to 0.95
        )
    }
}

// Valid output: Returns successfully
// Missing field: Returns error "Evidence must include citations"
```

### 2. Type Validation

Enforce correct data types:

```kotlin
val analysisTool = contextAwareTool("analyze_data") {
    description = "Data analysis with type checking"

    validate {
        // Type validation
        fieldType("result", FieldType.STRING)
        fieldType("score", FieldType.NUMBER)
        fieldType("items", FieldType.ARRAY)
        fieldType("metadata", FieldType.OBJECT)
        fieldType("isComplete", FieldType.BOOLEAN)
    }

    execute { params, context ->
        mapOf(
            "result" to "analysis complete",
            "score" to 85.5,
            "items" to listOf("item1", "item2"),
            "metadata" to mapOf("version" to "1.0"),
            "isComplete" to true
        )
    }
}
```

### 3. Range and Pattern Validation

Enforce value constraints:

```kotlin
val userTool = contextAwareTool("create_user") {
    description = "Create user with validation"

    validate {
        // Required fields
        requireField("email")
        requireField("age")
        requireField("username")

        // Pattern validation (email format)
        pattern(
            "email",
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"),
            "Invalid email format"
        )

        // Range validation (age between 18 and 120)
        range("age", 18.0, 120.0, "Age must be between 18 and 120")

        // Pattern validation (username alphanumeric)
        pattern(
            "username",
            Regex("^[a-zA-Z0-9_]{3,20}\$"),
            "Username must be 3-20 alphanumeric characters"
        )
    }

    execute { params, context ->
        mapOf(
            "email" to "user@example.com",
            "age" to 25,
            "username" to "john_doe"
        )
    }
}
```

### 4. Custom Validation Logic

Implement business rules:

```kotlin
val orderTool = contextAwareTool("create_order") {
    description = "Create order with business rules"

    validate {
        requireField("items")
        requireField("total")
        requireField("discount")

        // Custom rule: total must match items sum
        rule("Total must match sum of item prices") { output, context ->
            val items = output["items"] as? List<Map<String, Any>> ?: return@rule false
            val total = (output["total"] as? Number)?.toDouble() ?: return@rule false
            val discount = (output["discount"] as? Number)?.toDouble() ?: 0.0

            val itemsSum = items.sumOf {
                (it["price"] as? Number)?.toDouble() ?: 0.0
            }

            (itemsSum - discount) == total
        }
    }

    execute { params, context ->
        val items = listOf(
            mapOf("name" to "Item 1", "price" to 10.0),
            mapOf("name" to "Item 2", "price" to 20.0)
        )
        val discount = 5.0

        mapOf(
            "items" to items,
            "total" to 25.0,  // 30 - 5
            "discount" to discount
        )
    }
}
```

## Validation Rules

### Required Fields

Ensure fields exist and are not null:

```kotlin
validate {
    // Simple required field
    requireField("name")

    // With custom error message
    requireField("email", "Email is required for registration")

    // Multiple required fields
    requireField("firstName")
    requireField("lastName")
    requireField("dateOfBirth")
}
```

**Validation Logic:**
```kotlin
fun validateRequired(output: Any, field: String): Boolean {
    val map = output as? Map<*, *> ?: return false
    return map.containsKey(field) && map[field] != null
}
```

**Error Messages:**
- Default: "Required field '$field' is missing"
- Custom: Your provided message

### Field Type Validation

Enforce correct data types:

```kotlin
validate {
    fieldType("name", FieldType.STRING)
    fieldType("age", FieldType.NUMBER)
    fieldType("isActive", FieldType.BOOLEAN)
    fieldType("tags", FieldType.ARRAY)
    fieldType("metadata", FieldType.OBJECT)
}
```

**Supported Types:**

| FieldType | Kotlin Types | Example |
|-----------|--------------|---------|
| `STRING` | String | `"hello"` |
| `NUMBER` | Int, Long, Float, Double | `42`, `3.14` |
| `BOOLEAN` | Boolean | `true`, `false` |
| `ARRAY` | List, Array | `[1, 2, 3]` |
| `OBJECT` | Map | `{"key": "value"}` |

**Type Checking Logic:**
```kotlin
fun validateType(value: Any?, expectedType: FieldType): Boolean {
    return when (expectedType) {
        FieldType.STRING -> value is String
        FieldType.NUMBER -> value is Number
        FieldType.BOOLEAN -> value is Boolean
        FieldType.ARRAY -> value is List<*> || value is Array<*>
        FieldType.OBJECT -> value is Map<*, *>
    }
}
```

### Range Validation

Validate numeric values within bounds:

```kotlin
validate {
    // Basic range
    range("score", 0.0, 100.0)

    // With custom message
    range("age", 18.0, 120.0, "Age must be between 18 and 120")

    // Percentage validation
    range("confidence", 0.0, 1.0, "Confidence must be between 0 and 1")

    // Positive numbers
    range("price", 0.01, Double.MAX_VALUE, "Price must be positive")
}
```

**Validation Logic:**
```kotlin
fun validateRange(value: Any, min: Double, max: Double): Boolean {
    val numValue = (value as? Number)?.toDouble() ?: return false
    return numValue >= min && numValue <= max
}
```

### Pattern Validation

Validate strings against regex patterns:

```kotlin
validate {
    // Email validation
    pattern("email", Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))

    // Phone number (US)
    pattern("phone", Regex("^\\+?1?\\d{10}\$"))

    // URL validation
    pattern("url", Regex("^https?://[\\w.-]+\\.[a-z]{2,}.*\$"))

    // Username (alphanumeric, 3-20 chars)
    pattern("username", Regex("^[a-zA-Z0-9_]{3,20}\$"))

    // ZIP code (US)
    pattern("zipCode", Regex("^\\d{5}(-\\d{4})?\$"))

    // Credit card (basic)
    pattern("cardNumber", Regex("^\\d{13,19}\$"))
}
```

**Common Patterns:**

```kotlin
object ValidationPatterns {
    val EMAIL = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
    val PHONE_US = Regex("^\\+?1?\\d{10}\$")
    val URL = Regex("^https?://[\\w.-]+\\.[a-z]{2,}.*\$")
    val USERNAME = Regex("^[a-zA-Z0-9_]{3,20}\$")
    val ZIP_US = Regex("^\\d{5}(-\\d{4})?\$")
    val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$")
}

// Usage
validate {
    pattern("email", ValidationPatterns.EMAIL)
    pattern("id", ValidationPatterns.UUID)
}
```

## Field Types

### STRING

Validates string values:

```kotlin
validate {
    fieldType("name", FieldType.STRING)
    fieldType("description", FieldType.STRING)
}

// Valid:
output = mapOf("name" to "John", "description" to "User profile")

// Invalid:
output = mapOf("name" to 123, "description" to null)
```

### NUMBER

Validates numeric values (Int, Long, Float, Double):

```kotlin
validate {
    fieldType("age", FieldType.NUMBER)
    fieldType("price", FieldType.NUMBER)
    fieldType("score", FieldType.NUMBER)
}

// Valid:
output = mapOf("age" to 25, "price" to 19.99, "score" to 0.95)

// Invalid:
output = mapOf("age" to "25", "price" to null)
```

### BOOLEAN

Validates boolean values:

```kotlin
validate {
    fieldType("isActive", FieldType.BOOLEAN)
    fieldType("hasAccess", FieldType.BOOLEAN)
}

// Valid:
output = mapOf("isActive" to true, "hasAccess" to false)

// Invalid:
output = mapOf("isActive" to "true", "hasAccess" to 1)
```

### ARRAY

Validates array/list values:

```kotlin
validate {
    fieldType("tags", FieldType.ARRAY)
    fieldType("items", FieldType.ARRAY)
}

// Valid:
output = mapOf("tags" to listOf("tag1", "tag2"), "items" to arrayOf(1, 2, 3))

// Invalid:
output = mapOf("tags" to "tag1,tag2", "items" to null)
```

### OBJECT

Validates object/map values:

```kotlin
validate {
    fieldType("metadata", FieldType.OBJECT)
    fieldType("address", FieldType.OBJECT)
}

// Valid:
output = mapOf(
    "metadata" to mapOf("version" to "1.0"),
    "address" to mapOf("city" to "NYC", "zip" to "10001")
)

// Invalid:
output = mapOf("metadata" to "version:1.0", "address" to null)
```

## Custom Validation

### Basic Custom Rules

Define arbitrary validation logic:

**Two Overloads Available:**

The `rule()` function has two overloads for different use cases:

```kotlin
// Overload 1: Simple validation (no context needed)
validate {
    rule("Value must be non-empty") { output ->
        val value = output["someField"]
        value != null && value.toString().isNotEmpty()
    }
}

// Overload 2: Context-aware validation (access to AgentContext)
validate {
    rule("Tenant-specific validation") { output, context ->
        val tenantId = context?.get("tenantId") as? String
        val outputTenant = output["tenantId"] as? String
        tenantId == outputTenant
    }
}
```

**Which one to use?**

- **Use simple overload** `{ output -> Boolean }` when validation doesn't need context information
- **Use context-aware overload** `{ output, context -> Boolean }` when you need tenant, user, or other context data

**Examples:**

```kotlin
validate {
    // ✅ Simple validation - no context needed
    rule("Price must be positive") { output ->
        val price = (output["price"] as? Number)?.toDouble() ?: 0.0
        price > 0.0
    }

    // ✅ Context-aware validation - uses tenant info
    rule("Output matches request tenant") { output, context ->
        val requestTenant = context?.get("tenantId") as? String
        val outputTenant = output["tenantId"] as? String
        requestTenant == outputTenant
    }

    // ✅ Simple validation - field existence check
    rule("Required nested field") { output ->
        val data = output["data"] as? Map<*, *>
        data?.containsKey("id") == true
    }

    // ✅ Context-aware validation - user permissions
    rule("User has permission") { output, context ->
        val requiredRole = output["requiredRole"] as? String
        val userRoles = context?.get("roles") as? List<*>
        requiredRole in (userRoles ?: emptyList<String>())
    }
}
```

### Context-Aware Validation

Access AgentContext in custom rules for tenant-specific, user-specific, or session-specific validation:

```kotlin
validate {
    // Validate based on tenant
    rule("Premium features only for premium tenants") { output, context ->
        val isPremiumFeature = output["isPremium"] as? Boolean ?: false
        val tenantTier = context.metadata["tier"] as? String

        // Allow premium features only for premium tenants
        !isPremiumFeature || tenantTier == "premium"
    }
}
```

### Complex Business Rules

Implement multi-field validation:

```kotlin
validate {
    // Order total validation
    rule("Order total must match items and discounts") { output, context ->
        val items = output["items"] as? List<Map<String, Any>> ?: return@rule false
        val subtotal = output["subtotal"] as? Number ?: return@rule false
        val discount = (output["discount"] as? Number)?.toDouble() ?: 0.0
        val tax = (output["tax"] as? Number)?.toDouble() ?: 0.0
        val total = output["total"] as? Number ?: return@rule false

        // Calculate expected total
        val itemsSum = items.sumOf { (it["price"] as? Number)?.toDouble() ?: 0.0 }
        val expectedTotal = itemsSum - discount + tax

        // Allow small floating point errors
        Math.abs(expectedTotal - total.toDouble()) < 0.01
    }
}
```

### Data Consistency Rules

Validate relationships between fields:

```kotlin
validate {
    // Start date before end date
    rule("End date must be after start date") { output, context ->
        val startDate = output["startDate"] as? String ?: return@rule true
        val endDate = output["endDate"] as? String ?: return@rule true

        try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            end.isAfter(start)
        } catch (e: Exception) {
            false
        }
    }

    // Conditional requirements
    rule("Discount reason required when discount > 10%") { output, context ->
        val discount = (output["discount"] as? Number)?.toDouble() ?: 0.0
        val reason = output["discountReason"] as? String

        discount <= 10.0 || !reason.isNullOrBlank()
    }
}
```

## Usage Patterns

### Pattern 1: LLM Output Validation

**Scenario:** Validate structured extraction from LLM.

```kotlin
val extractTool = contextAwareTool("extract_entities") {
    description = "Extract entities from text using LLM"

    param("text", "string", "Text to analyze", required = true)

    validate {
        // Ensure output structure
        requireField("entities", "Must extract entities")
        requireField("confidence")
        fieldType("entities", FieldType.ARRAY)
        fieldType("confidence", FieldType.NUMBER)

        // Confidence must be reasonable
        range("confidence", 0.0, 1.0)

        // Custom: entities must not be empty
        rule("Must extract at least one entity") { output, _ ->
            val entities = output["entities"] as? List<*> ?: return@rule false
            entities.isNotEmpty()
        }

        // Custom: each entity must have required fields
        rule("Each entity must have type and value") { output, _ ->
            val entities = output["entities"] as? List<Map<String, Any>> ?: return@rule false
            entities.all {
                it.containsKey("type") && it.containsKey("value")
            }
        }
    }

    execute { params, context ->
        val text = params["text"] as String

        // Call LLM
        val llmResponse = llmClient.extract(text)

        // Return structured output (validated automatically)
        mapOf(
            "entities" to llmResponse.entities,
            "confidence" to llmResponse.confidence
        )
    }
}
```

### Pattern 2: Evidence JSON Validation

**Scenario:** Enforce evidence format with citations.

```kotlin
val evidenceTool = contextAwareTool("generate_evidence") {
    description = "Generate evidence with mandatory citations"

    validate {
        // Required fields
        requireField("statement", "Evidence must have a statement")
        requireField("citations", "Evidence must cite sources")
        requireField("confidence")

        // Type validation
        fieldType("statement", FieldType.STRING)
        fieldType("citations", FieldType.ARRAY)
        fieldType("confidence", FieldType.NUMBER)
        fieldType("metadata", FieldType.OBJECT)

        // Range validation
        range("confidence", 0.0, 1.0, "Confidence must be 0-1")

        // Custom: At least one citation
        rule("Must provide at least one citation") { output, _ ->
            val citations = output["citations"] as? List<*> ?: return@rule false
            citations.isNotEmpty()
        }

        // Custom: Each citation must be valid
        rule("Each citation must have source and page") { output, _ ->
            val citations = output["citations"] as? List<Map<String, Any>> ?: return@rule false
            citations.all {
                it.containsKey("source") && it.containsKey("page")
            }
        }

        // Custom: High confidence requires multiple citations
        rule("High confidence (>0.9) requires 2+ citations") { output, _ ->
            val confidence = (output["confidence"] as? Number)?.toDouble() ?: return@rule true
            val citations = output["citations"] as? List<*> ?: return@rule true

            confidence <= 0.9 || citations.size >= 2
        }
    }

    execute { params, context ->
        // Generate evidence
        mapOf(
            "statement" to "The product meets specifications",
            "citations" to listOf(
                mapOf("source" to "Manual", "page" to 42),
                mapOf("source" to "Test Report", "page" to 15)
            ),
            "confidence" to 0.95,
            "metadata" to mapOf("timestamp" to System.currentTimeMillis())
        )
    }
}
```

### Pattern 3: Form Validation

**Scenario:** Validate user registration form.

```kotlin
val registerTool = contextAwareTool("register_user") {
    description = "Register new user with validation"

    validate {
        // Required fields
        requireField("email")
        requireField("password")
        requireField("age")
        requireField("termsAccepted")

        // Email format
        pattern(
            "email",
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"),
            "Invalid email format"
        )

        // Password strength
        rule("Password must be strong") { output, _ ->
            val password = output["password"] as? String ?: return@rule false
            password.length >= 8 &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() }
        }

        // Age requirement
        range("age", 18.0, 150.0, "Must be 18 or older")

        // Terms acceptance
        fieldType("termsAccepted", FieldType.BOOLEAN)
        rule("Must accept terms and conditions") { output, _ ->
            output["termsAccepted"] as? Boolean == true
        }
    }

    execute { params, context ->
        // Register user
        val user = userService.register(params)

        mapOf(
            "email" to user.email,
            "password" to user.password,
            "age" to user.age,
            "termsAccepted" to true
        )
    }
}
```

### Pattern 4: API Response Validation

**Scenario:** Validate third-party API responses.

```kotlin
val weatherTool = contextAwareTool("get_weather") {
    description = "Get weather from external API"

    param("city", "string", required = true)

    validate {
        // Ensure API returned expected fields
        requireField("temperature", "API must return temperature")
        requireField("condition")
        requireField("humidity")

        // Type validation
        fieldType("temperature", FieldType.NUMBER)
        fieldType("condition", FieldType.STRING)
        fieldType("humidity", FieldType.NUMBER)

        // Reasonable ranges
        range("temperature", -100.0, 150.0, "Temperature out of range")
        range("humidity", 0.0, 100.0, "Humidity must be 0-100%")

        // Custom: condition must be valid
        rule("Weather condition must be valid") { output, _ ->
            val condition = output["condition"] as? String ?: return@rule false
            listOf("sunny", "cloudy", "rainy", "snowy").contains(condition.lowercase())
        }
    }

    execute { params, context ->
        val city = params["city"] as String

        // Call external API
        val response = weatherApiClient.getWeather(city)

        // Return normalized output (validated!)
        mapOf(
            "temperature" to response.temp,
            "condition" to response.condition,
            "humidity" to response.humidity
        )
    }
}
```

### Pattern 5: Financial Data Validation

**Scenario:** Validate financial calculations.

```kotlin
val invoiceTool = contextAwareTool("create_invoice") {
    description = "Create invoice with financial validation"

    validate {
        // Required fields
        requireField("items")
        requireField("subtotal")
        requireField("tax")
        requireField("total")
        requireField("currency")

        // Types
        fieldType("items", FieldType.ARRAY)
        fieldType("subtotal", FieldType.NUMBER)
        fieldType("tax", FieldType.NUMBER)
        fieldType("total", FieldType.NUMBER)
        fieldType("currency", FieldType.STRING)

        // Positive values
        range("subtotal", 0.0, Double.MAX_VALUE)
        range("tax", 0.0, Double.MAX_VALUE)
        range("total", 0.0, Double.MAX_VALUE)

        // Currency code format
        pattern("currency", Regex("^[A-Z]{3}\$"), "Invalid currency code")

        // Custom: Total calculation
        rule("Total must equal subtotal + tax") { output, _ ->
            val subtotal = (output["subtotal"] as? Number)?.toDouble() ?: return@rule false
            val tax = (output["tax"] as? Number)?.toDouble() ?: return@rule false
            val total = (output["total"] as? Number)?.toDouble() ?: return@rule false

            // Allow 0.01 tolerance for floating point
            Math.abs((subtotal + tax) - total) < 0.01
        }

        // Custom: Items sum matches subtotal
        rule("Items must sum to subtotal") { output, _ ->
            val items = output["items"] as? List<Map<String, Any>> ?: return@rule false
            val subtotal = (output["subtotal"] as? Number)?.toDouble() ?: return@rule false

            val itemsSum = items.sumOf {
                val price = (it["price"] as? Number)?.toDouble() ?: 0.0
                val quantity = (it["quantity"] as? Number)?.toInt() ?: 0
                price * quantity
            }

            Math.abs(itemsSum - subtotal) < 0.01
        }
    }

    execute { params, context ->
        // Generate invoice
        val items = listOf(
            mapOf("name" to "Item 1", "price" to 10.00, "quantity" to 2),
            mapOf("name" to "Item 2", "price" to 15.00, "quantity" to 1)
        )
        val subtotal = 35.00
        val tax = 3.50
        val total = 38.50

        mapOf(
            "items" to items,
            "subtotal" to subtotal,
            "tax" to tax,
            "total" to total,
            "currency" to "USD"
        )
    }
}
```

### Pattern 6: Nested Object Validation

**Scenario:** Validate complex nested structures.

```kotlin
val orderTool = contextAwareTool("create_order") {
    description = "Create order with nested validation"

    validate {
        // Top-level fields
        requireField("customer")
        requireField("items")
        requireField("shipping")
        requireField("payment")

        // Types
        fieldType("customer", FieldType.OBJECT)
        fieldType("items", FieldType.ARRAY)
        fieldType("shipping", FieldType.OBJECT)
        fieldType("payment", FieldType.OBJECT)

        // Custom: Validate customer object
        rule("Customer must have name and email") { output, _ ->
            val customer = output["customer"] as? Map<String, Any> ?: return@rule false
            customer.containsKey("name") && customer.containsKey("email")
        }

        // Custom: Validate items array
        rule("Items must have SKU and quantity") { output, _ ->
            val items = output["items"] as? List<Map<String, Any>> ?: return@rule false
            items.isNotEmpty() && items.all {
                it.containsKey("sku") && it.containsKey("quantity")
            }
        }

        // Custom: Validate shipping address
        rule("Shipping must have complete address") { output, _ ->
            val shipping = output["shipping"] as? Map<String, Any> ?: return@rule false
            listOf("street", "city", "state", "zip").all {
                shipping.containsKey(it)
            }
        }

        // Custom: Validate payment method
        rule("Payment must specify method") { output, _ ->
            val payment = output["payment"] as? Map<String, Any> ?: return@rule false
            payment.containsKey("method")
        }
    }

    execute { params, context ->
        mapOf(
            "customer" to mapOf(
                "name" to "John Doe",
                "email" to "john@example.com"
            ),
            "items" to listOf(
                mapOf("sku" to "ABC-123", "quantity" to 2),
                mapOf("sku" to "DEF-456", "quantity" to 1)
            ),
            "shipping" to mapOf(
                "street" to "123 Main St",
                "city" to "New York",
                "state" to "NY",
                "zip" to "10001"
            ),
            "payment" to mapOf(
                "method" to "credit_card",
                "last4" to "1234"
            )
        )
    }
}
```

## Advanced Techniques

### Conditional Validation

Apply validation rules based on context or output values:

```kotlin
validate {
    // Base validation
    requireField("type")
    fieldType("type", FieldType.STRING)

    // Conditional validation based on type
    rule("Premium type requires premium fields") { output, _ ->
        val type = output["type"] as? String ?: return@rule true

        if (type == "premium") {
            // Require additional fields for premium
            output.containsKey("premiumFeatures") &&
            output.containsKey("supportLevel")
        } else {
            true  // No additional requirements for other types
        }
    }
}
```

### Validation Groups

Reuse validation logic:

```kotlin
// Define reusable validation groups
object ValidationGroups {
    val emailValidation: OutputValidatorBuilder.() -> Unit = {
        requireField("email")
        pattern("email", Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))
    }

    val addressValidation: OutputValidatorBuilder.() -> Unit = {
        requireField("street")
        requireField("city")
        requireField("state")
        requireField("zip")
    }
}

// Use in tools
val tool1 = contextAwareTool("tool1") {
    validate {
        apply(ValidationGroups.emailValidation)
        apply(ValidationGroups.addressValidation)
    }
    execute { _, _ -> /* ... */ }
}
```

### Validation Helpers

Create helper functions for common patterns:

```kotlin
fun OutputValidatorBuilder.validateEmail(field: String) {
    requireField(field, "$field is required")
    pattern(
        field,
        Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"),
        "Invalid $field format"
    )
}

fun OutputValidatorBuilder.validatePercentage(field: String) {
    requireField(field)
    fieldType(field, FieldType.NUMBER)
    range(field, 0.0, 100.0, "$field must be 0-100%")
}

fun OutputValidatorBuilder.validateUUID(field: String) {
    requireField(field)
    pattern(
        field,
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$"),
        "$field must be a valid UUID"
    )
}

// Usage
validate {
    validateEmail("primaryEmail")
    validateEmail("secondaryEmail")
    validatePercentage("discountRate")
    validateUUID("transactionId")
}
```

### Cross-Field Validation

Validate relationships between multiple fields:

```kotlin
validate {
    // Ensure passwords match
    rule("Passwords must match") { output, _ ->
        val password = output["password"] as? String
        val confirmPassword = output["confirmPassword"] as? String
        password != null && password == confirmPassword
    }

    // Ensure dates are in order
    rule("End date must be after start date") { output, _ ->
        val startDate = output["startDate"] as? Long ?: return@rule true
        val endDate = output["endDate"] as? Long ?: return@rule true
        endDate > startDate
    }

    // Ensure quantity doesn't exceed stock
    rule("Quantity cannot exceed available stock") { output, _ ->
        val quantity = (output["quantity"] as? Number)?.toInt() ?: return@rule true
        val stock = (output["availableStock"] as? Number)?.toInt() ?: return@rule true
        quantity <= stock
    }
}
```

## Error Handling

### Validation Errors

When validation fails, a clear error message is returned:

```kotlin
val tool = contextAwareTool("test") {
    validate {
        requireField("email", "Email is required")
    }
    execute { _, _ ->
        mapOf("name" to "John")  // Missing email!
    }
}

// Execution result:
result.isSuccess == false
result.error == "Email is required"
```

### Multiple Validation Errors

Validation stops at the first error (fail-fast):

```kotlin
validate {
    requireField("field1")   // Fails here!
    requireField("field2")   // Not checked
    requireField("field3")   // Not checked
}

// Only first error is returned:
// "Required field 'field1' is missing"
```

### Custom Error Messages

Provide helpful error messages:

```kotlin
validate {
    // ❌ Generic error
    requireField("email")
    // Error: "Required field 'email' is missing"

    // ✅ Helpful error
    requireField("email", "Please provide a valid email address for notifications")
    // Error: "Please provide a valid email address for notifications"
}
```

### Error Context

Include context in error messages:

```kotlin
validate {
    rule("Order total validation") { output, context ->
        val tenantId = context.tenantId
        val total = output["total"] as? Number

        // Include tenant in error (logged, not returned to user)
        logger.error("Validation failed for tenant $tenantId: invalid total $total")

        false
    }
}
```

## Best Practices

### 1. Validate Early and Clearly

```kotlin
// ✅ GOOD: Clear, explicit validation
validate {
    requireField("email", "User email is required")
    requireField("age", "User age is required")
    range("age", 18.0, 120.0, "Age must be between 18 and 120")
}

// ❌ BAD: Validation buried in execute block
execute { params, context ->
    val email = params["email"] as? String
    if (email == null) throw IllegalArgumentException("Email required")
    // ...
}
```

### 2. Use Specific Error Messages

```kotlin
// ✅ GOOD: Specific, actionable
requireField("email", "Email is required for account recovery")
range("age", 18.0, 120.0, "You must be at least 18 years old")

// ❌ BAD: Generic, unhelpful
requireField("email")
range("age", 18.0, 120.0)
```

### 3. Validate Types Before Ranges

```kotlin
// ✅ GOOD: Check type first
validate {
    fieldType("score", FieldType.NUMBER)
    range("score", 0.0, 100.0)
}

// ❌ BAD: Range fails with wrong type
validate {
    range("score", 0.0, 100.0)  // Crashes if "score" is not a number
}
```

### 4. Group Related Validations

```kotlin
// ✅ GOOD: Grouped logically
validate {
    // User fields
    requireField("name")
    requireField("email")
    validateEmail("email")

    // Address fields
    requireField("street")
    requireField("city")
    requireField("zip")

    // Payment fields
    requireField("cardNumber")
    pattern("cardNumber", Regex("^\\d{13,19}\$"))
}
```

### 5. Use Custom Rules for Business Logic

```kotlin
// ✅ GOOD: Business logic in custom rule
validate {
    rule("Premium users only") { output, context ->
        val isPremiumFeature = output["isPremium"] as? Boolean ?: false
        val userTier = context.metadata["tier"] as? String

        !isPremiumFeature || userTier == "premium"
    }
}

// ❌ BAD: Business logic in execute block
execute { params, context ->
    val result = performAction()
    if (result.isPremium && context.metadata["tier"] != "premium") {
        return@execute ToolResult.error("Premium only")
    }
    // ...
}
```

### 6. Test Validation Logic

```kotlin
@Test
fun `validation enforces required fields`() = runBlocking {
    val tool = contextAwareTool("test") {
        validate {
            requireField("name")
            requireField("email")
        }
        execute { _, _ ->
            mapOf("name" to "John")  // Missing email
        }
    }

    val result = tool.execute(emptyMap())

    assertFalse(result.isSuccess)
    assertTrue(result.error.contains("email"))
}
```

### 7. Handle Null vs Missing Fields

```kotlin
// Both are invalid for requireField:
output = mapOf()                    // Field missing
output = mapOf("field" to null)     // Field is null

// Use optional validation for nullable fields:
validate {
    // Required field: must exist and not be null
    requireField("requiredField")

    // Optional field: if present, must match type
    rule("Optional field must be string if present") { output, _ ->
        val optionalField = output["optionalField"]
        optionalField == null || optionalField is String
    }
}
```

## Testing

### Unit Tests

Test validation rules:

```kotlin
@Test
fun `requireField validates correctly`() = runBlocking {
    val tool = contextAwareTool("test") {
        validate {
            requireField("name")
        }
        execute { _, _ ->
            mapOf("email" to "test@example.com")  // Missing "name"
        }
    }

    val result = tool.execute(emptyMap())

    assertFalse(result.isSuccess)
    assertTrue(result.error.contains("name"))
}

@Test
fun `range validation works`() = runBlocking {
    val tool = contextAwareTool("test") {
        validate {
            range("score", 0.0, 100.0)
        }
        execute { _, _ ->
            mapOf("score" to 150)  // Out of range
        }
    }

    val result = tool.execute(emptyMap())

    assertFalse(result.isSuccess)
    assertTrue(result.error.contains("range"))
}
```

### Integration Tests

Test full tool workflow:

```kotlin
@Test
fun `user registration validates and saves`() = runBlocking {
    val registerTool = contextAwareTool("register") {
        validate {
            requireField("email")
            pattern("email", Regex("^.+@.+\\..+\$"))
            requireField("age")
            range("age", 18.0, 120.0)
        }
        execute { params, _ ->
            userRepository.save(params)
            params
        }
    }

    // Valid registration
    val validResult = registerTool.execute(mapOf(
        "email" to "john@example.com",
        "age" to 25
    ))
    assertTrue(validResult.isSuccess)

    // Invalid email
    val invalidEmail = registerTool.execute(mapOf(
        "email" to "invalid-email",
        "age" to 25
    ))
    assertFalse(invalidEmail.isSuccess)

    // Invalid age
    val invalidAge = registerTool.execute(mapOf(
        "email" to "john@example.com",
        "age" to 15
    ))
    assertFalse(invalidAge.isSuccess)
}
```

### Custom Rule Tests

Test complex validation logic:

```kotlin
@Test
fun `custom rule validates business logic`() = runBlocking {
    val tool = contextAwareTool("test") {
        validate {
            rule("Total must equal items sum") { output, _ ->
                val items = output["items"] as? List<Map<String, Any>> ?: return@rule false
                val total = (output["total"] as? Number)?.toDouble() ?: return@rule false

                val sum = items.sumOf {
                    (it["price"] as? Number)?.toDouble() ?: 0.0
                }

                sum == total
            }
        }
        execute { _, _ ->
            mapOf(
                "items" to listOf(
                    mapOf("price" to 10.0),
                    mapOf("price" to 20.0)
                ),
                "total" to 25.0  // Wrong! Should be 30.0
            )
        }
    }

    val result = tool.execute(emptyMap())
    assertFalse(result.isSuccess)
}
```

## Troubleshooting

### Validation Passing When It Shouldn't

**Problem:** Validation doesn't catch invalid data.

**Solutions:**

1. **Check rule return value:**
   ```kotlin
   // ❌ Wrong: Always returns true
   rule("Check value") { output, _ ->
       val value = output["value"]
       value != null
       true  // Oops! Should return the condition
   }

   // ✅ Correct
   rule("Check value") { output, _ ->
       val value = output["value"]
       value != null  // Returns condition result
   }
   ```

2. **Type casting issues:**
   ```kotlin
   // ❌ Wrong: Cast fails silently
   rule("Check score") { output, _ ->
       val score = output["score"] as Double  // ClassCastException caught!
       score > 0
   }

   // ✅ Correct: Safe casting
   rule("Check score") { output, _ ->
       val score = (output["score"] as? Number)?.toDouble() ?: return@rule false
       score > 0
   }
   ```

### Validation Failing When It Shouldn't

**Problem:** Valid data is rejected.

**Solutions:**

1. **Check type matching:**
   ```kotlin
   // Int vs Double mismatch
   output = mapOf("score" to 85)  // Int
   range("score", 0.0, 100.0)     // Expects Number (works!)
   fieldType("score", FieldType.NUMBER)  // Also works!
   ```

2. **Floating point precision:**
   ```kotlin
   // ❌ Wrong: Exact equality
   rule("Check total") { output, _ ->
       val total = (output["total"] as Number).toDouble()
       total == 29.99
   }

   // ✅ Correct: Tolerance
   rule("Check total") { output, _ ->
       val total = (output["total"] as Number).toDouble()
       Math.abs(total - 29.99) < 0.01
   }
   ```

### Performance Issues

**Problem:** Validation is slow for large outputs.

**Solutions:**

1. **Optimize custom rules:**
   ```kotlin
   // ❌ Slow: Multiple iterations
   rule("Validate items") { output, _ ->
       val items = output["items"] as List<Map<String, Any>>
       items.all { it.containsKey("id") } &&  // Iteration 1
       items.all { it.containsKey("name") } &&  // Iteration 2
       items.all { it.containsKey("price") }    // Iteration 3
   }

   // ✅ Fast: Single iteration
   rule("Validate items") { output, _ ->
       val items = output["items"] as List<Map<String, Any>>
       items.all {
           it.containsKey("id") &&
           it.containsKey("name") &&
           it.containsKey("price")
       }
   }
   ```

2. **Avoid expensive operations:**
   ```kotlin
   // ❌ Slow: Complex regex on large strings
   pattern("content", Regex("^.{0,1000000}\$"))  // Slow!

   // ✅ Fast: Simple length check
   rule("Content length limit") { output, _ ->
       val content = output["content"] as? String ?: return@rule false
       content.length <= 1000000
   }
   ```

## API Reference

### OutputValidator

```kotlin
class OutputValidator(private val rules: List<ValidationRule>) {
    fun validate(output: Any, context: AgentContext? = null): ValidationResult

    companion object {
        fun fromBuilder(builder: OutputValidatorBuilder.() -> Unit): OutputValidator
    }
}
```

### ValidationResult

```kotlin
sealed class ValidationResult {
    data class Valid(val output: Any) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()

    val isValid: Boolean
    val error: String?
}
```

### OutputValidatorBuilder

```kotlin
class OutputValidatorBuilder {
    // Required field validation
    fun requireField(field: String, message: String? = null)

    // Type validation
    fun fieldType(field: String, expectedType: FieldType, message: String? = null)

    // Range validation
    fun range(field: String, min: Double, max: Double, message: String? = null)

    // Pattern validation
    fun pattern(field: String, regex: Regex, message: String? = null)

    // Custom validation
    fun rule(description: String, validator: (Any, AgentContext?) -> Boolean)
}
```

### FieldType

```kotlin
enum class FieldType {
    STRING,    // String values
    NUMBER,    // Numeric values (Int, Long, Float, Double)
    BOOLEAN,   // Boolean values
    ARRAY,     // List or Array
    OBJECT     // Map
}
```

### ValidationRule (Internal)

```kotlin
sealed class ValidationRule {
    data class RequiredField(val field: String, val message: String) : ValidationRule()
    data class FieldTypeValidation(val field: String, val expectedType: FieldType, val message: String?) : ValidationRule()
    data class RangeValidation(val field: String, val min: Double, val max: Double, val message: String?) : ValidationRule()
    data class PatternValidation(val field: String, val regex: Regex, val message: String?) : ValidationRule()
    data class CustomRule(val description: String, val validator: (Any, AgentContext?) -> Boolean) : ValidationRule()
}
```

## Related Documentation

- [Tool-Level Caching](../performance/tool-caching.md) - Cache validated outputs
- [Context-Aware Tools](./context-aware-tools.md) - Build tools with context
- [Tool Pipeline DSL](../orchestration/tool-pipeline.md) - Chain validated tools
- [Error Handling](../error-handling/overview.md) - Handle validation errors

## Summary

Output Validation DSL provides:

✅ **Declarative validation** with clean syntax
✅ **Type safety** for all output fields
✅ **Range checking** for numeric values
✅ **Pattern matching** for string formats
✅ **Custom business rules** for complex logic
✅ **Context-aware validation** for tenant-specific rules
✅ **Clear error messages** for debugging
✅ **Fail-fast execution** for quick feedback

Start enforcing output quality today! 🚀
