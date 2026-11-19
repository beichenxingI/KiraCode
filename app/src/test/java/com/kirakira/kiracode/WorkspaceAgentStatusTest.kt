package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.ChatSession
import com.kirakira.kiracode.ui.workspace.AgentStatus
import com.kirakira.kiracode.ui.workspace.PendingInteraction
import com.kirakira.kiracode.ui.workspace.deriveAgentStatus
import com.kirakira.kiracode.ui.workspace.formatRecordTime
import com.kirakira.kiracode.ui.workspace.isBusy
import com.kirakira.kiracode.ui.workspace.isWaitingForUser
import com.kirakira.kiracode.ui.workspace.label
import com.kirakira.kiracode.ui.workspace.sessionsToRecords
import com.kirakira.kiracode.ui.workspace.sortedFavoritesFirst
import com.kirakira.kiracode.ui.workspace.toDotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工作台回归覆盖（阶段 B/C/D）：
 * AgentStatus 单一派生源的优先级、状态映射、工作记录排序与时间格式。
 */
class WorkspaceAgentStatusTest {

    // ==================== AgentStatus 派生优先级 ====================

    @Test
    fun `等待用户优先于执行中`() {
        val status = deriveAgentStatus(
            isSending = true, isExecuting = true,
            pendingInteraction = PendingInteraction.Question(
                cardId = "q1", question = "?", options = emptyList()
            ),
            errorMessage = null
        )
        assertEquals(AgentStatus.WAITING_USER, status)
        assertTrue(status.isWaitingForUser)
    }

    @Test
    fun `等待计划与等待 Diff 按挂起类型区分`() {
        assertEquals(
            AgentStatus.WAITING_PLAN,
            deriveAgentStatus(false, true, PendingInteraction.Plan(cardId = "p1"), null)
        )
        assertEquals(
            AgentStatus.WAITING_DIFF,
            deriveAgentStatus(
                false, true,
                PendingInteraction.Diff(cardId = "d1", fieldKey = "name", fieldLabel = "角色名称"),
                null
            )
        )
    }

    @Test
    fun `流式优先于工具执行中`() {
        assertEquals(
            AgentStatus.STREAMING,
            deriveAgentStatus(true, true, null, "陈旧错误")
        )
        assertTrue(AgentStatus.STREAMING.isBusy)
    }

    @Test
    fun `执行中且非流式为工具执行中`() {
        assertEquals(
            AgentStatus.TOOL_RUNNING,
            deriveAgentStatus(false, true, null, null)
        )
        assertTrue(AgentStatus.TOOL_RUNNING.isBusy)
    }

    @Test
    fun `无执行且无等待时错误成立`() {
        val status = deriveAgentStatus(false, false, null, "执行失败：超时")
        assertEquals(AgentStatus.ERROR, status)
        assertFalse(status.isBusy)
    }

    @Test
    fun `全部空闲时为空闲`() {
        val status = deriveAgentStatus(false, false, null, null)
        assertEquals(AgentStatus.IDLE, status)
        assertFalse(status.isBusy)
        assertFalse(status.isWaitingForUser)
    }

    // ==================== 状态映射 ====================

    @Test
    fun `状态映射到状态点与中文标签`() {
        assertEquals("空闲", AgentStatus.IDLE.label())
        assertEquals("生成中", AgentStatus.STREAMING.label())
        assertEquals("执行工具", AgentStatus.TOOL_RUNNING.label())
        assertEquals("等待用户", AgentStatus.WAITING_USER.label())
        assertEquals("等待计划确认", AgentStatus.WAITING_PLAN.label())
        assertEquals("等待 Diff 决定", AgentStatus.WAITING_DIFF.label())
        assertEquals("失败", AgentStatus.ERROR.label())

        assertEquals(com.kirakira.kiracode.ui.components.DotStatus.IDLE, AgentStatus.IDLE.toDotStatus())
        assertEquals(com.kirakira.kiracode.ui.components.DotStatus.RUNNING, AgentStatus.STREAMING.toDotStatus())
        assertEquals(com.kirakira.kiracode.ui.components.DotStatus.RUNNING, AgentStatus.TOOL_RUNNING.toDotStatus())
        assertEquals(com.kirakira.kiracode.ui.components.DotStatus.WAITING, AgentStatus.WAITING_USER.toDotStatus())
        assertEquals(com.kirakira.kiracode.ui.components.DotStatus.WAITING, AgentStatus.WAITING_PLAN.toDotStatus())
        assertEquals(com.kirakira.kiracode.ui.components.DotStatus.WAITING, AgentStatus.WAITING_DIFF.toDotStatus())
        assertEquals(com.kirakira.kiracode.ui.components.DotStatus.ERROR, AgentStatus.ERROR.toDotStatus())
    }

    // ==================== 工作记录 ====================

    @Test
    fun `会话映射为工作记录并保留置顶`() {
        val sessions = listOf(
            ChatSession(id = 1, uuid = "u1", title = "赫尔墨", lastMessageTime = 1000L, isPinned = true),
            ChatSession(id = 2, uuid = "u2", title = "", lastMessageTime = 2000L)
        )
        val records = sessionsToRecords(sessions)
        assertEquals(2, records.size)
        assertEquals("赫尔墨", records[0].cardName)
        assertEquals(true, records[0].isFavorite)
        assertEquals("", records[1].cardName)
        assertEquals(false, records[1].isFavorite)
    }

    @Test
    fun `置顶优先其后按时间倒序`() {
        val records = sessionsToRecords(
            listOf(
                ChatSession(id = 1, uuid = "u1", title = "A", lastMessageTime = 1000L),
                ChatSession(id = 2, uuid = "u2", title = "B", lastMessageTime = 3000L, isPinned = true),
                ChatSession(id = 3, uuid = "u3", title = "C", lastMessageTime = 2000L, isPinned = true),
                ChatSession(id = 4, uuid = "u4", title = "D", lastMessageTime = 4000L)
            )
        ).sortedFavoritesFirst()
        assertEquals(listOf(2L, 3L, 4L, 1L), records.map { it.id })
    }

    @Test
    fun `记录时间格式为日期时间各段`() {
        val formatted = formatRecordTime(1760000000000L)
        assertEquals(16, formatted.length)
        assertEquals('-', formatted[4])
        assertEquals(':', formatted[13])
    }

    @Test
    fun `无效时间戳返回空串`() {
        assertEquals("", formatRecordTime(0L))
        assertEquals("", formatRecordTime(-1L))
    }
}
