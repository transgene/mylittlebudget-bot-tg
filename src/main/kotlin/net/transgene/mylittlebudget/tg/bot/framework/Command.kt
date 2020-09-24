package net.transgene.mylittlebudget.tg.bot.framework

interface Command {
    fun call(callMessageId: Long): List<Action>
}

interface TextCommand : Command {
    fun consumeText(messageId: Long, text: String): List<Action>
}

interface ButtonPressCommand<P> : Command {
    fun consumeButtonPress(messageId: Long, payload: P): List<Action>
}

data class CallbackPayload(
    val conversationId: String,
    val buttonId: Int
)

