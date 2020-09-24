package net.transgene.mylittlebudget.tg.bot.framework

import com.github.kotlintelegrambot.Bot
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

class Conversation(command: Command, bot: Bot, val chatId: Long, initialMessageId: Long) {
    private var finished: Boolean = false
    private val buttonPayloads: LinkedHashMap<Int, Any> = LinkedHashMap()
    private val messages: MutableList<Long> = ArrayList()

    init {
        messages.add(initialMessageId)
    }

    val id = UUID.randomUUID().toString()

    val bot: Bot = bot
        get() {
            checkFinished()
            return field
        }

    val command: Command = command
        get() {
            checkFinished()
            return field
        }

    fun getButtonPayloadById(id: Int): Any? = buttonPayloads[id]

    fun addButtonPayload(payload: Any): Int {
        checkFinished()
        val buttonId = nextButtonId()
        buttonPayloads[buttonId] = payload
        return buttonId
    }

    fun getMessages(): List<Long> = ArrayList(messages)

    fun logMessage(messageId: Long) {
        checkFinished()
        messages.add(messageId)
    }

    fun finish() {
        finished = true
    }

    fun isFinished(): Boolean {
        return finished
    }

    private fun checkFinished() {
        if (finished) {
            throw IllegalStateException("Conversation has already finished")
        }
    }

    private fun nextButtonId(): Int {
        return if (buttonPayloads.isEmpty()) {
            Int.MIN_VALUE
        } else {
            val maxId = buttonPayloads.keys.last()
            if (maxId == Int.MAX_VALUE) {
                throw IllegalStateException("Button payloads registry overflown during the conversation")
            }
            maxId + 1
        }
    }
}
