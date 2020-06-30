package net.transgene.mylittlebudget.tg.bot.framework

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import java.util.*
import kotlin.collections.LinkedHashMap

interface Command {
    fun call(callMessageId: Long): List<Action>
}

interface TextCommand : Command {
    fun consumeText(messageId: Long, text: String): List<Action>
}

interface ButtonPressCommand<P> : Command {
    fun consumeButtonPress(messageId: Long, payload: P): List<Action>
}

class Conversation(val command: Command, val chatId: Long, val bot: Bot) {
    val id = UUID.randomUUID().toString()

    private val buttonPayloads: LinkedHashMap<Int, Any> = LinkedHashMap()

    fun getButtonPayloadById(id: Int): Any? {
        return buttonPayloads[id]
    }

    fun addButtonPayload(payload: Any): Int {
        val buttonId = nextButtonId()
        buttonPayloads[buttonId] = payload
        return buttonId
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

data class CallbackPayload(
    val conversationId: String,
    val buttonId: Int
)

interface Action {
    fun perform(conversation: Conversation)
}

class SendMessage(private val text: String, private val buttons: List<Button<out Any>>? = null): Action {
    override fun perform(conversation: Conversation) {
        val kbButtons = buttons?.map {
            val buttonId = conversation.addButtonPayload(it.payload)
            listOf(InlineKeyboardButton(text = it.text, callbackData = "${conversation.id}#$buttonId"))
        }
        conversation.bot.sendMessage(
            chatId = conversation.chatId,
            text = text,
            replyMarkup = kbButtons?.let { InlineKeyboardMarkup(it) }
        )
    }
}

class EditMessage(private val messageId: Long, private val text: String, private val buttons: List<Button<out Any>>?) :
    Action {
    override fun perform(conversation: Conversation) {
        val kbButtons: List<List<InlineKeyboardButton>>? = buttons?.map {
            val buttonId = conversation.addButtonPayload(it.payload)
            listOf(InlineKeyboardButton(text = it.text, callbackData = "${conversation.id}#$buttonId"))
        }
        conversation.bot.editMessageText(
            chatId = conversation.chatId,
            messageId = messageId,
            text = text,
            replyMarkup = kbButtons?.let { InlineKeyboardMarkup(it) }
        )
    }
}

class DeleteMessage(private val messageId: Long): Action {
    override fun perform(conversation: Conversation) {
        conversation.bot.deleteMessage(chatId = conversation.chatId, messageId = messageId)
    }
}

object Finish: Action {
    override fun perform(conversation: Conversation) {}
}

data class Button<P>(val text: String, val payload: P)
