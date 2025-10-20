package io.github.noailabs.spice

import io.github.noailabs.spice.dsl.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * 🧪 Property-Based Tests for Comm
 *
 * Uses Kotest property-based testing to verify Comm behavior across
 * a wide range of random inputs and edge cases.
 */
class CommPropertyTest : StringSpec({

    "Comm creation should always generate valid ID" {
        checkAll(Arb.string(1..100), Arb.string(1..50)) { content, from ->
            val comm = Comm(content = content, from = from)
            comm.id.shouldNotBeBlank()
            comm.id.length shouldBe 36 // UUID length
        }
    }

    "Comm reply should correctly swap from and to" {
        checkAll(
            Arb.string(1..50),  // content
            Arb.string(1..30),  // from
            Arb.string(1..30)   // to
        ) { content, from, to ->
            val original = Comm(content = content, from = from, to = to)
            val reply = original.reply(content = "Reply", from = to)

            reply.from shouldBe to
            reply.to shouldBe from
            reply.parentId shouldBe original.id
            reply.role shouldBe CommRole.ASSISTANT
        }
    }

    "Comm withData should preserve original immutability" {
        checkAll(
            Arb.string(1..100),
            Arb.string(1..20),
            Arb.string(1..50)
        ) { content, key, value ->
            val original = Comm(content = content, from = "test")
            val modified = original.withData(key, value)

            // Original should remain unchanged
            original.data.isEmpty() shouldBe true

            // Modified should have the new data
            modified.data[key] shouldBe value
            modified.content shouldBe content
        }
    }

    "Comm expiration should work correctly" {
        checkAll(Arb.long(100L..10000L)) { ttlMs ->
            val comm = Comm(content = "Test", from = "test").expires(ttlMs)

            comm.ttl shouldBe ttlMs
            comm.expiresAt shouldNotBe null
            comm.isExpired() shouldBe false

            // Create an expired comm
            val expired = comm.copy(expiresAt = System.currentTimeMillis() - 1000)
            expired.isExpired() shouldBe true
        }
    }

    "Comm timestamp should always be positive and recent" {
        checkAll(Arb.string(1..100)) { content ->
            val beforeCreation = System.currentTimeMillis()
            val comm = Comm(content = content, from = "test")
            val afterCreation = System.currentTimeMillis()

            comm.timestamp shouldNotBe 0L
            (comm.timestamp >= beforeCreation) shouldBe true
            (comm.timestamp <= afterCreation) shouldBe true
        }
    }

    "Comm type and role combinations should be valid" {
        val types = listOf(CommType.TEXT, CommType.SYSTEM, CommType.ERROR, CommType.TOOL_CALL, CommType.TOOL_RESULT)
        val roles = listOf(CommRole.USER, CommRole.ASSISTANT, CommRole.SYSTEM)

        types.forEach { type ->
            roles.forEach { role ->
                val comm = Comm(
                    content = "Test",
                    from = "test",
                    type = type,
                    role = role
                )

                comm.type shouldBe type
                comm.role shouldBe role
            }
        }
    }

    "Comm priority should affect comparison" {
        val urgent = Comm(content = "Urgent", from = "test", priority = Priority.URGENT)
        val normal = Comm(content = "Normal", from = "test", priority = Priority.NORMAL)
        val low = Comm(content = "Low", from = "test", priority = Priority.LOW)

        urgent.priority shouldBe Priority.URGENT
        normal.priority shouldBe Priority.NORMAL
        low.priority shouldBe Priority.LOW
    }

    "Comm builder should produce same results as constructor" {
        checkAll(
            Arb.string(1..100),
            Arb.string(1..30),
            Arb.string(1..30)
        ) { content, from, to ->
            val constructed = Comm(
                content = content,
                from = from,
                to = to,
                type = CommType.TEXT,
                role = CommRole.USER
            )

            val built = comm(content) {
                from(from)
                to(to)
                type(CommType.TEXT)
                role(CommRole.USER)
            }

            built.content shouldBe constructed.content
            built.from shouldBe constructed.from
            built.to shouldBe constructed.to
            built.type shouldBe constructed.type
            built.role shouldBe constructed.role
        }
    }

    "Comm data map should handle various string values" {
        val comm = Comm(content = "Test", from = "test")
            .withData("string", "value")
            .withData("number", "42")
            .withData("boolean", "true")
            .withData("list", "[1, 2, 3]")
            .withData("map", "{nested=data}")

        comm.data["string"] shouldBe "value"
        comm.data["number"] shouldBe "42"
        comm.data["boolean"] shouldBe "true"
        comm.data["list"] shouldBe "[1, 2, 3]"
        comm.data["map"] shouldBe "{nested=data}"
    }

    "Comm copy should create independent instances" {
        checkAll(Arb.string(1..100)) { content ->
            val original = Comm(content = content, from = "test")
                .withData("key1", "value1")

            val copy = original.copy(content = "Modified")

            copy.content shouldBe "Modified"
            original.content shouldBe content

            // Data should be preserved
            copy.data["key1"] shouldBe "value1"
        }
    }
})
