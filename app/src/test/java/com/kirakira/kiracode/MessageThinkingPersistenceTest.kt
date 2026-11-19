package com.kirakira.kiracode

import com.kirakira.kiracode.data.mapper.MessageMapper
import com.kirakira.kiracode.domain.model.ChatMessage
import com.kirakira.kiracode.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 思维链持久化映射：Message ↔ MessageEntity ↔ ChatMessage。
 */
class MessageThinkingPersistenceTest {

    @Test
    fun `thinking survives domain entity chat mapping`() {
        val mapper = MessageMapper()
        val domain = Message.createAssistantMessage(
            sessionId = 7L,
            content = "正文",
            thinking = "先思考，再回答。"
        )

        val entity = mapper.toEntity(domain)
        assertEquals("先思考，再回答。", entity.thinking)

        val restored = mapper.toDomain(entity)
        assertEquals("先思考，再回答。", restored.thinking)

        val chat = ChatMessage.fromMessage(restored)
        assertEquals("先思考，再回答。", chat.thinking)
        assertEquals("正文", chat.content)
    }
}
