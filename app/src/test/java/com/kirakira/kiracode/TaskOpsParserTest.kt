package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.AgentTask
import com.kirakira.kiracode.domain.agent.TaskOp
import com.kirakira.kiracode.domain.agent.TaskOpsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务板指令（task.*）解析与应用测试 —— 数据面板 AI 读写闭环。
 */
class TaskOpsParserTest {

    @Test
    fun `parse task add with kv body`() {
        val reply = """
            我先拆解任务再逐步推进。

            ```card-update
            [task.add]
            title: 设计世界观框架
            detail: 南疆边城，瘴气与药铺
            status: PENDING
            [/task.add]
            ```
        """.trimIndent()
        val ops = TaskOpsParser.parse(reply)
        assertEquals(1, ops.size)
        val add = ops[0] as TaskOp.Add
        assertEquals("设计世界观框架", add.title)
        assertTrue(add.detail.contains("瘴气"))
    }

    @Test
    fun `parse self closing status and delete`() {
        val reply = """
            ```card-update
            [task.status id=a1b2 status=DONE]
            [task.status id=世界观 title=x status=RUNNING]
            [task.delete id=c3d4]
            ```
        """.trimIndent()
        val ops = TaskOpsParser.parse(reply)
        assertEquals(3, ops.size)
        assertTrue(ops[0] is TaskOp.SetStatus)
        assertEquals("DONE", (ops[0] as TaskOp.SetStatus).newStatus)
        assertEquals("a1b2", (ops[0] as TaskOp.SetStatus).taskIdOrTitle)
        assertTrue(ops[2] is TaskOp.Delete)
    }

    @Test
    fun `apply task ops to board`() {
        var board = emptyList<AgentTask>()
        board = TaskOp.Add("写角色设定").applyTo(board)
        board = TaskOp.Add("补世界书", detail = "3 条", status = AgentTask.STATUS_RUNNING).applyTo(board)
        assertEquals(2, board.size)

        board = TaskOp.SetStatus("写角色设定", AgentTask.STATUS_DONE).applyTo(board)
        assertEquals(AgentTask.STATUS_DONE, board[0].status)

        board = TaskOp.Delete("补世界书").applyTo(board)
        assertEquals(1, board.size)
        assertEquals("写角色设定", board[0].title)
    }

    @Test
    fun `invalid status is rejected`() {
        val reply = """
            ```card-update
            [task.status id=t1 status=WAT]
            ```
        """.trimIndent()
        assertEquals(0, TaskOpsParser.parse(reply).size)
    }
}
