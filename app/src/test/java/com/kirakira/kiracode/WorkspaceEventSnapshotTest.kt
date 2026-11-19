package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.AgentTask
import com.kirakira.kiracode.domain.agent.PlanBook
import com.kirakira.kiracode.domain.agent.PlanPart
import com.kirakira.kiracode.ui.workspace.AgentCardUi
import com.kirakira.kiracode.ui.workspace.PlanStepUi
import com.kirakira.kiracode.ui.workspace.SerializableAuditIssue
import com.kirakira.kiracode.ui.workspace.WorkspaceEventSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话事件快照序列化回归（阶段 B）：
 * 全部事件卡类型 round-trip、计划/任务快照 round-trip、脏数据回退、体积上限。
 */
class WorkspaceEventSnapshotTest {

    private fun sampleCards(): List<AgentCardUi> = listOf(
        AgentCardUi.ToolCall(
            id = "t1", timestamp = 1000L, desc = "设置角色名称",
            functionName = "update_card", isAiGuess = false,
            fieldKey = "name", fieldLabel = "角色名称"
        ),
        AgentCardUi.Observation(id = "o1", timestamp = 1100L, content = "已更新", isError = false),
        AgentCardUi.Observation(id = "o2", timestamp = 1200L, content = "解析失败", isError = true),
        AgentCardUi.Question(
            id = "q1", timestamp = 1300L, question = "性格成因？",
            options = listOf("A", "B"), about = "性格", answered = null, skipped = false
        ),
        AgentCardUi.Plan(
            id = "p1", timestamp = 1400L,
            steps = listOf(PlanStepUi("创建骨架", "先写人设"), PlanStepUi("补充世界书")),
            status = AgentCardUi.Plan.STATUS_PENDING
        ),
        AgentCardUi.Progress(id = "pr1", timestamp = 1500L, done = 1, total = 8, title = "创建骨架"),
        AgentCardUi.BudgetDecision(
            id = "bd1", timestamp = 1550L,
            completedSummary = "名字、描述、世界书(2条)",
            pendingSteps = listOf("补充示例对话", "添加正则"),
            iterationsUsed = 5
        ),
        AgentCardUi.Diff(
            id = "d1", timestamp = 1600L, fieldKey = "first_mes", fieldLabel = "开场白",
            oldValue = "旧", newValue = "新", decision = null
        ),
        AgentCardUi.Info(id = "i1", timestamp = 1700L, text = "自动草稿", kind = AgentCardUi.Info.Kind.INFO),
        AgentCardUi.Info(id = "i2", timestamp = 1800L, text = "已从流式切换为整段返回", kind = AgentCardUi.Info.Kind.DEGRADED),
        AgentCardUi.Info(id = "i3", timestamp = 1900L, text = "执行失败", kind = AgentCardUi.Info.Kind.FAIL),
        AgentCardUi.Audit(
            id = "a1",
            timestamp = 2000L,
            cardName = "汐音",
            entryCount = 2,
            estimatedTokens = 1200,
            errorCount = 1,
            warningCount = 1,
            infoCount = 0,
            issues = listOf(
                SerializableAuditIssue(
                    severity = "ERROR",
                    entryUid = 1L,
                    entryComment = "港口",
                    issueCode = "EMPTY_KEYS",
                    description = "条目触发词为空",
                    suggestion = "添加触发词"
                )
            )
        )
    )

    @Test
    fun `snapshot round trip preserves all card types`() {
        val snapshot = WorkspaceEventSnapshot.of(
            cards = sampleCards(),
            planBook = PlanBook(
                goal = "做一张卡",
                parts = listOf(
                    PlanPart(id = "s1", title = "创建骨架", detail = "先写人设", status = PlanPart.STATUS_DONE),
                    PlanPart(id = "s2", title = "补充世界书", status = PlanPart.STATUS_PENDING)
                )
            ),
            tasks = listOf(
                AgentTask(id = "tk1", title = "校对正文", status = AgentTask.STATUS_RUNNING)
            )
        )

        val decoded = WorkspaceEventSnapshot.decode(WorkspaceEventSnapshot.encode(snapshot))

        assertEquals(snapshot.cards, decoded.cards)
        assertEquals(snapshot.planBook, decoded.planBook)
        assertEquals(snapshot.tasks, decoded.tasks)
        assertEquals(12, decoded.cards.size)
        assertEquals(1, decoded.planBook.doneCount)
    }

    @Test
    fun `blank and invalid json fall back to empty snapshot`() {
        assertEquals(WorkspaceEventSnapshot(), WorkspaceEventSnapshot.decode(null))
        assertEquals(WorkspaceEventSnapshot(), WorkspaceEventSnapshot.decode(""))
        assertEquals(WorkspaceEventSnapshot(), WorkspaceEventSnapshot.decode("[]"))
        assertEquals(WorkspaceEventSnapshot(), WorkspaceEventSnapshot.decode("not json"))
        assertEquals(WorkspaceEventSnapshot(), WorkspaceEventSnapshot.decode("{\"cards\":1}"))
    }

    @Test
    fun `decode keeps unknown future fields ignored`() {
        val json = """{"cards":[],"planBook":{"goal":"g","parts":[],"futureField":1},"tasks":[]}"""
        val decoded = WorkspaceEventSnapshot.decode(json)
        assertEquals("g", decoded.planBook.goal)
        assertTrue(decoded.tasks.isEmpty())
    }

    @Test
    fun `snapshot card limit keeps only latest cards`() {
        val many = (1..150).map { i ->
            AgentCardUi.Info(id = "i$i", timestamp = i.toLong() * 10, text = "x$i")
        }
        val snapshot = WorkspaceEventSnapshot.of(cards = many, planBook = null, tasks = emptyList())
        assertEquals(WorkspaceEventSnapshot.CARD_LIMIT, snapshot.cards.size)
        assertEquals("i150", snapshot.cards.last().id)
    }

    @Test
    fun `snapshot round trip preserves token stats`() {
        val snapshot = WorkspaceEventSnapshot(
            cards = sampleCards(),
            planBook = PlanBook(goal = "g"),
            tasks = listOf(AgentTask(id = "tk1", title = "t", status = AgentTask.STATUS_RUNNING)),
            promptTokensTotal = 12345L,
            completionTokensTotal = 6789L,
            requestCount = 42,
            cacheHitRate = 0.87f,
            processedTokens = 777
        )

        val decoded = WorkspaceEventSnapshot.decode(WorkspaceEventSnapshot.encode(snapshot))

        assertEquals(12345L, decoded.promptTokensTotal)
        assertEquals(6789L, decoded.completionTokensTotal)
        assertEquals(42, decoded.requestCount)
        assertEquals(0.87f, decoded.cacheHitRate, 0.0001f)
        assertEquals(777, decoded.processedTokens)
    }

    @Test
    fun `decode old json without token fields keeps defaults zero`() {
        val json = """{"cards":[],"planBook":{"goal":"g","parts":[]},"tasks":[]}"""
        val decoded = WorkspaceEventSnapshot.decode(json)
        assertEquals(0L, decoded.promptTokensTotal)
        assertEquals(0, decoded.requestCount)
        assertEquals(0f, decoded.cacheHitRate, 0.0001f)
    }
}
