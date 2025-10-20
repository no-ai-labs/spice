package io.github.noailabs.spice.error

import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * 🧪 Tests for SpiceResult and error handling
 */
class SpiceResultTest : StringSpec({

    "SpiceResult success should contain value" {
        val result = SpiceResult.success("test value")

        result.isSuccess shouldBe true
        result.isFailure shouldBe false
        result.getOrNull() shouldBe "test value"
        result.getOrThrow() shouldBe "test value"
    }

    "SpiceResult failure should contain error" {
        val error = SpiceError.agentError("Test error", "agent-1")
        val result: SpiceResult<String> = SpiceResult.failure(error)

        result.isSuccess shouldBe false
        result.isFailure shouldBe true
        result.getOrNull() shouldBe null
    }

    "SpiceResult map should transform success value" {
        val result = SpiceResult.success(5)
            .map { it * 2 }
            .map { it + 3 }

        result.getOrNull() shouldBe 13
    }

    "SpiceResult map should preserve failure" {
        val error = SpiceError.validationError("Invalid input")
        val result: SpiceResult<Int> = SpiceResult.failure<Int>(error)
            .map { it * 2 }
            .map { it + 3 }

        result.isFailure shouldBe true
        (result as SpiceResult.Failure).error shouldBe error
    }

    "SpiceResult flatMap should chain operations" {
        fun divide(a: Int, b: Int): SpiceResult<Int> {
            return if (b == 0) {
                SpiceResult.failure(SpiceError.validationError("Division by zero"))
            } else {
                SpiceResult.success(a / b)
            }
        }

        val success = SpiceResult.success(10)
            .flatMap { divide(it, 2) }
            .flatMap { divide(it, 5) }

        success.getOrNull() shouldBe 1

        val failure = SpiceResult.success(10)
            .flatMap { divide(it, 0) }
            .flatMap { divide(it, 5) }

        failure.isFailure shouldBe true
    }

    "SpiceResult recover should handle errors" {
        val result: SpiceResult<String> = SpiceResult.failure<String>(
            SpiceError.agentError("Failed")
        ).recover { "recovered value" }

        result.getOrNull() shouldBe "recovered value"
    }

    "SpiceResult recoverWith should handle errors with Result" {
        val result: SpiceResult<String> = SpiceResult.failure<String>(
            SpiceError.agentError("Failed")
        ).recoverWith {
            SpiceResult.success("recovered via recoverWith")
        }

        result.getOrNull() shouldBe "recovered via recoverWith"
    }

    "SpiceResult fold should handle both cases" {
        val success = SpiceResult.success(42)
        val successValue = success.fold(
            onSuccess = { it * 2 },
            onFailure = { 0 }
        )
        successValue shouldBe 84

        val failure: SpiceResult<Int> = SpiceResult.failure(SpiceError.agentError("Error"))
        val failureValue = failure.fold(
            onSuccess = { it * 2 },
            onFailure = { 0 }
        )
        failureValue shouldBe 0
    }

    "SpiceResult catching should catch exceptions" {
        val success = SpiceResult.catching {
            "success"
        }
        success.getOrNull() shouldBe "success"

        val failure = SpiceResult.catching {
            throw IllegalArgumentException("Invalid")
        }
        failure.isFailure shouldBe true
    }

    "SpiceResult catchingSuspend should catch async exceptions" {
        runBlocking {
            val success = SpiceResult.catchingSuspend {
                kotlinx.coroutines.delay(10)
                "async success"
            }
            success.getOrNull() shouldBe "async success"

            val failure = SpiceResult.catchingSuspend {
                kotlinx.coroutines.delay(10)
                throw IllegalArgumentException("Async error")
            }
            failure.isFailure shouldBe true
        }
    }

    "SpiceResult onSuccess should execute side effect" {
        var called = false
        SpiceResult.success("test")
            .onSuccess { called = true }

        called shouldBe true

        called = false
        SpiceResult.failure<String>(SpiceError.agentError("Error"))
            .onSuccess { called = true }

        called shouldBe false
    }

    "SpiceResult onFailure should execute side effect" {
        var called = false
        SpiceResult.failure<String>(SpiceError.agentError("Error"))
            .onFailure { called = true }

        called shouldBe true

        called = false
        SpiceResult.success("test")
            .onFailure { called = true }

        called shouldBe false
    }

    "SpiceResult getOrElse should provide default" {
        val success = SpiceResult.success(42)
        success.getOrElse(0) shouldBe 42

        val failure: SpiceResult<Int> = SpiceResult.failure(SpiceError.agentError("Error"))
        failure.getOrElse(0) shouldBe 0
    }

    "SpiceResult getOrElse with lambda should compute default" {
        val failure: SpiceResult<String> = SpiceResult.failure(
            SpiceError.agentError("Something went wrong")
        )
        val result = failure.getOrElse { error ->
            "Error: ${error.message}"
        }

        result shouldBe "Error: Something went wrong"
    }

    "Comm toResult should convert error comms" {
        val errorComm = Comm(
            content = "Error message",
            from = "system",
            type = CommType.ERROR
        )

        val result = errorComm.toResult()
        result.isFailure shouldBe true

        val successComm = Comm(
            content = "Success message",
            from = "user",
            type = CommType.TEXT
        )

        val successResult = successComm.toResult()
        successResult.isSuccess shouldBe true
    }

    "SpiceError withContext should add context" {
        val error = SpiceError.agentError("Test error")
            .withContext("key1" to "value1", "key2" to 123)

        error.context["key1"] shouldBe "value1"
        error.context["key2"] shouldBe 123
    }

    "SpiceError fromException should map exceptions correctly" {
        val illegalArg = IllegalArgumentException("Invalid argument")
        val error1 = SpiceError.fromException(illegalArg)
        error1.shouldBeInstanceOf<SpiceError.ValidationError>()

        val illegalState = IllegalStateException("Invalid state")
        val error2 = SpiceError.fromException(illegalState)
        error2.shouldBeInstanceOf<SpiceError.ConfigurationError>()

        val unknown = RuntimeException("Unknown error")
        val error3 = SpiceError.fromException(unknown)
        error3.shouldBeInstanceOf<SpiceError.UnknownError>()
    }

    "catchSpiceError should catch and convert exceptions" {
        val success = catchSpiceError {
            42
        }
        success.getOrNull() shouldBe 42

        val failure = catchSpiceError {
            throw IllegalArgumentException("Invalid")
        }
        failure.isFailure shouldBe true
    }

    "catchSpiceErrorSuspend should catch async exceptions" {
        runBlocking {
            val success = catchSpiceErrorSuspend {
                kotlinx.coroutines.delay(10)
                "async result"
            }
            success.getOrNull() shouldBe "async result"

            val failure = catchSpiceErrorSuspend {
                kotlinx.coroutines.delay(10)
                throw IllegalArgumentException("Async error")
            }
            failure.isFailure shouldBe true
        }
    }

    "SpiceException should wrap SpiceError" {
        val error = SpiceError.toolError("Tool failed", "calculator")
        val exception = SpiceException(error)

        exception.error shouldBe error
        exception.code shouldBe "TOOL_ERROR"
        exception.message shouldBe "Tool failed"
    }

    "SpiceResult mapError should transform error" {
        val result: SpiceResult<String> = SpiceResult.failure<String>(
            SpiceError.agentError("Original error")
        ).mapError { error ->
            SpiceError.commError("Transformed: ${error.message}")
        }

        result.isFailure shouldBe true
        val error = (result as SpiceResult.Failure).error
        error.shouldBeInstanceOf<SpiceError.CommError>()
        error.message shouldBe "Transformed: Original error"
    }
})
