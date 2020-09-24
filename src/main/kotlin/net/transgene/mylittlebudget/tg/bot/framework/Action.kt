package net.transgene.mylittlebudget.tg.bot.framework

import com.github.kotlintelegrambot.entities.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.google.gson.JsonSyntaxException

interface Action {
    fun perform(conversation: Conversation)
}

class SendMessage(private val text: String, private val buttons: List<Button<out Any>>? = null, private val cancellable: Boolean = true) : Action {
    override fun perform(conversation: Conversation) {
        val (res, ex) = conversation.bot.sendMessage(
            chatId = conversation.chatId,
            text = text,
            replyMarkup = InlineKeyboardMarkup(getButtons(conversation, buttons, cancellable))
        )
        if (ex != null) {
            throw ActionException(ex)
        } else {
            val messageId = res?.body()?.result?.messageId ?: throw ActionException("MessageId not found in response")
            conversation.logMessage(messageId)
        }
    }
}

class EditMessage(private val messageId: Long, private val text: String, private val buttons: List<Button<out Any>>? = null, private val cancellable: Boolean = true) :
    Action {
    override fun perform(conversation: Conversation) {
        val (_, ex) = conversation.bot.editMessageText(
            chatId = conversation.chatId,
            messageId = messageId,
            text = text,
            replyMarkup = InlineKeyboardMarkup(getButtons(conversation, buttons, cancellable))
        )
        if (ex != null) {
            throw ActionException(ex)
        }
    }
}

class DeleteMessage(private val messageId: Long) : Action {
    override fun perform(conversation: Conversation) {
        val (_, ex) = conversation.bot.deleteMessage(chatId = conversation.chatId, messageId = messageId)
        if (ex != null) {
            throw ActionException(ex)
        }
    }
}

object Finish : Action {
    override fun perform(conversation: Conversation) {
        val messages = conversation.getMessages().dropLast(1)
        finishConversation(messages, conversation)
    }

}

object Cancel : Action {
    override fun perform(conversation: Conversation) {
        val messages = conversation.getMessages()
        finishConversation(messages, conversation)
    }
}

data class Button<P>(val text: String, val payload: P)

class ActionException : RuntimeException {
    constructor(cause: Exception) : super(cause)
    constructor(message: String) : super(message)
}

private fun finishConversation(messages: List<Long>, conversation: Conversation) {
    messages.forEach {
        val (_, ex) = conversation.bot.deleteMessage(chatId = conversation.chatId, messageId = it)
        if (ex != null
            //FIXME Find the cause of the JsonSyntaxException (#11)
            && !(ex is JsonSyntaxException && ex.message?.startsWith("java.lang.IllegalStateException: Expected BEGIN_OBJECT but was BOOLEAN", true) == true)) {
            //TODO Do not throw the error to the user. Log instead, try to delete as many messages as possible and finish the conversation anyway (#7)
            throw ActionException(ex)
        }
    }
    conversation.finish()
}

private fun createButton(conversation: Conversation, text: String, payload: Any): List<InlineKeyboardButton> {
    val buttonId = conversation.addButtonPayload(payload)
    return listOf(InlineKeyboardButton(text = text, callbackData = "${conversation.id}#$buttonId"))
}

private fun getButtons(conversation: Conversation, userButtons: List<Button<out Any>>?, addCancelButton: Boolean): List<List<InlineKeyboardButton>> {
    val buttons = userButtons?.map { createButton(conversation, it.text, it.payload) }?.toMutableList()
        ?: mutableListOf()

    if (addCancelButton) {
        buttons.add(createButton(conversation, "[Отменить]", Cancel))
    }
    return buttons
}
