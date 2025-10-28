package io.github.noailabs.spice.handoff

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.Comm
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HandoffTest {

    @Test
    fun `test handoff request creation with DSL`() = runTest {
        // Given: A communication
        val comm = Comm(
            content = "고객 문의: 환불 요청",
            from = "customer-123"
        )

        // When: Create handoff
        val handoffComm = comm.handoff(fromAgentId = "cs-bot") {
            reason = "복잡한 환불 문의로 상담원 연결 필요"
            priority = HandoffPriority.HIGH
            toAgentId = "human-agent-pool"

            task(
                description = "고객 환불 요청 처리",
                type = HandoffTaskType.INVESTIGATE,
                required = true
            )

            task(
                description = "고객에게 처리 결과 안내",
                type = HandoffTaskType.RESPOND,
                required = true
            )

            addHistory("Customer: 환불하고 싶어요")
            addHistory("Bot: 상담원 연결 중...")

            addMetadata("customer_id", "customer-123")
            addMetadata("session_id", "session-xyz")
        }

        // Then: Handoff comm created correctly
        assertTrue(handoffComm.isHandoff())
        assertEquals("human-agent-pool", handoffComm.to)
        assertEquals("cs-bot", handoffComm.from)

        val request = handoffComm.getHandoffRequest()
        assertNotNull(request)
        assertEquals("복잡한 환불 문의로 상담원 연결 필요", request.reason)
        assertEquals(HandoffPriority.HIGH, request.priority)
        assertEquals(2, request.tasks.size)
        assertEquals("고객 환불 요청 처리", request.tasks[0].description)
        assertEquals(HandoffTaskType.INVESTIGATE, request.tasks[0].type)
        assertEquals(2, request.conversationHistory.size)
        assertEquals("customer-123", request.metadata["customer_id"])
    }

    @Test
    fun `test handoff return with response`() = runTest {
        // Given: Handoff comm
        val handoffComm = Comm(content = "원래 요청", from = "customer").handoff("cs-bot") {
            reason = "상담원 필요"
            task("문의 응대", HandoffTaskType.RESPOND, true)
        }

        val request = handoffComm.getHandoffRequest()!!
        val taskId = request.tasks[0].id

        // When: Human returns response
        val returnComm = handoffComm.returnFromHandoff(
            humanAgentId = "human-agent-john",
            result = "환불 처리가 완료되었습니다",
            completedTasks = listOf(
                CompletedTask(
                    taskId = taskId,
                    result = "고객에게 환불 안내 완료",
                    success = true
                )
            ),
            notes = "고객 만족"
        )

        // Then: Return comm created correctly
        assertTrue(returnComm.isReturnFromHandoff())
        assertEquals("human-agent-john", returnComm.from)
        assertEquals("cs-bot", returnComm.to)
        assertEquals("환불 처리가 완료되었습니다", returnComm.content)

        val response = returnComm.getHandoffResponse()
        assertNotNull(response)
        assertEquals("human-agent-john", response.humanAgentId)
        assertEquals("환불 처리가 완료되었습니다", response.result)
        assertEquals(1, response.completedTasks.size)
        assertTrue(response.returnToBot)
        assertEquals("고객 만족", response.notes)
    }

    @Test
    fun `test handoff with AgentContext propagation`() = runTest {
        // Given: Comm with AgentContext
        val comm = Comm(
            content = "테스트",
            from = "customer",
            context = ExecutionContext.of(mapOf(
                "userId" to "user-123",
                "tenantId" to "tenant-abc"
            ))
        )

        // When: Create handoff
        val handoffComm = comm.handoff("cs-bot") {
            reason = "테스트"
            task("작업", HandoffTaskType.RESPOND, true)
        }

        // Then: Context propagated
        assertNotNull(handoffComm.context)
        assertEquals("user-123", handoffComm.context?.userId)
        assertEquals("tenant-abc", handoffComm.context?.tenantId)

        // Handoff metadata also added to context
        val handoffId = handoffComm.data[HandoffMetadataKeys.HANDOFF_ID]
        assertNotNull(handoffId)
        assertEquals("true", handoffComm.context?.get(HandoffMetadataKeys.IS_HANDOFF))
    }

    @Test
    fun `test handoff priority levels`() = runTest {
        val comm = Comm(content = "test", from = "user")

        val urgentHandoff = comm.handoff("agent") {
            reason = "긴급"
            priority = HandoffPriority.URGENT
            task("긴급 처리", HandoffTaskType.RESPOND, true)
        }

        val normalHandoff = comm.handoff("agent") {
            reason = "일반"
            priority = HandoffPriority.NORMAL
            task("일반 처리", HandoffTaskType.RESPOND, true)
        }

        assertEquals(HandoffPriority.URGENT, urgentHandoff.getHandoffRequest()?.priority)
        assertEquals(HandoffPriority.NORMAL, normalHandoff.getHandoffRequest()?.priority)
    }

    @Test
    fun `test handoff with multiple tasks`() = runTest {
        val comm = Comm(content = "복잡한 요청", from = "customer")

        val handoffComm = comm.handoff("cs-bot") {
            reason = "다단계 처리 필요"

            task("1단계: 조사", HandoffTaskType.INVESTIGATE, required = true)
            task("2단계: 승인", HandoffTaskType.APPROVE, required = true)
            task("3단계: 응답", HandoffTaskType.RESPOND, required = true)
            task("추가: 에스컬레이션", HandoffTaskType.ESCALATE, required = false)
        }

        val request = handoffComm.getHandoffRequest()!!
        assertEquals(4, request.tasks.size)
        assertEquals(3, request.tasks.count { it.required })
        assertEquals(1, request.tasks.count { !it.required })
    }

    @Test
    fun `test task types`() = runTest {
        val comm = Comm(content = "test", from = "user")

        val handoffComm = comm.handoff("agent") {
            reason = "다양한 작업 유형"
            task("응답", HandoffTaskType.RESPOND, true)
            task("승인", HandoffTaskType.APPROVE, true)
            task("검토", HandoffTaskType.REVIEW, true)
            task("조사", HandoffTaskType.INVESTIGATE, true)
            task("에스컬레이션", HandoffTaskType.ESCALATE, true)
            task("커스텀", HandoffTaskType.CUSTOM, true)
        }

        val tasks = handoffComm.getHandoffRequest()!!.tasks
        assertEquals(6, tasks.size)
        assertTrue(tasks.any { it.type == HandoffTaskType.RESPOND })
        assertTrue(tasks.any { it.type == HandoffTaskType.APPROVE })
        assertTrue(tasks.any { it.type == HandoffTaskType.REVIEW })
        assertTrue(tasks.any { it.type == HandoffTaskType.INVESTIGATE })
        assertTrue(tasks.any { it.type == HandoffTaskType.ESCALATE })
        assertTrue(tasks.any { it.type == HandoffTaskType.CUSTOM })
    }
}
