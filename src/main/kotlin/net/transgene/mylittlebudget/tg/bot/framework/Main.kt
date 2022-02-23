package net.transgene.mylittlebudget.tg.bot.framework

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.extensions.filters.Filter
import net.transgene.mylittlebudget.tg.bot.commands.ExpenseCommand
import net.transgene.mylittlebudget.tg.bot.commands.IncomeCommand
import net.transgene.mylittlebudget.tg.bot.sheets.GoogleSheetsService
import org.ehcache.Cache
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import java.nio.file.Paths
import java.time.Duration

fun main(args: Array<String>) {
    val bot = bot {
        val conversations: Cache<Long, Conversation> = getConversationStorage()
        val config = loadAppConfig(args.ifEmpty { arrayOf(".") }.let { Paths.get(it[0]) })
        val registeredChats = config.users.keys

        token = config.botToken
        dispatch {
            fun getSheetsService(chatId: Long): GoogleSheetsService =
                GoogleSheetsService(config.googleCredentials, config.users.getValue(chatId))

            fun callCommand(bot: Bot, update: Update, command: Command) {
                val chatId = getChatId(update.message)
                val messageId = getMessageId(update.message)
                if (!registeredChats.contains(chatId) || messageId == null) {
                    return
                }

                tryExecute(bot, chatId) {
                    val conversation = Conversation(command, bot, chatId, messageId)
                    conversations.put(chatId, conversation)

                    val actions = command.call(messageId)
                    actions.forEach { it.perform(conversation) }
                }
            }

            command("exp") { bot, update ->
                callCommand(bot, update, ExpenseCommand(getSheetsService(getChatId(update.message))))
            }

            command("inc") { bot, update ->
                callCommand(bot, update, IncomeCommand(getSheetsService(getChatId(update.message))))
            }

            callbackQuery { bot, update ->
                val chatId = getChatId(update.callbackQuery?.message)
                val messageId = getMessageId(update.callbackQuery?.message)
                if (!registeredChats.contains(chatId) || messageId == null) {
                    return@callbackQuery
                }

                val conversation = getConversation(conversations, chatId) ?: return@callbackQuery
                val callbackData = update.callbackQuery?.data ?: return@callbackQuery
                val callbackPayload = parseCallbackData(callbackData)
                val buttonPayload = conversation.getButtonPayloadById(callbackPayload.buttonId)
                if (conversation.id != callbackPayload.conversationId || buttonPayload == null) {
                    bot.sendMessage(chatId, "Эта кнопка принадлежит завершенной или отмененной операции.")
                    return@callbackQuery
                } else if (buttonPayload is Action) {
                    buttonPayload.perform(conversation)
                    return@callbackQuery
                } else if (conversation.command !is ButtonPressCommand<*>) {
                    bot.sendMessage(chatId, "Команда, которую вы вызвали, не принимает нажатия кнопок.")
                    return@callbackQuery
                }

                tryExecute(bot, chatId) {
                    @Suppress("UNCHECKED_CAST")
                    val actions = (conversation.command as ButtonPressCommand<Any>).consumeButtonPress(messageId, buttonPayload)
                    actions.forEach { it.perform(conversation) }
                }
            }

            message(Filter.Text) { bot, update ->
                val chatId = getChatId(update.message)
                val messageId = getMessageId(update.message)
                if (!registeredChats.contains(chatId) || messageId == null) {
                    return@message
                }
                val conversation = getConversation(conversations, chatId) ?: return@message
                val msgText = update.message?.text ?: return@message
                if (conversation.command !is TextCommand) {
                    bot.sendMessage(chatId, "Команда, которую вы вызвали, не принимает текстовые сообщения.")
                    return@message
                }

                tryExecute(bot, chatId) {
                    conversation.logMessage(messageId)
                    val actions = (conversation.command as TextCommand).consumeText(messageId, msgText)
                    actions.forEach { it.perform(conversation) }
                }
            }
        }
    }

    bot.startPolling()
}

private fun tryExecute(bot: Bot, chatId: Long, code: () -> Unit) {
    try {
        code.invoke()
    } catch (e: Exception) {
        bot.sendMessage(
            chatId,
            "Произошла непредвиденная ошибка при выполнении операции.\nПожалуйста, попробуйте еще раз или обратитесь к администратору бота."
        )
        throw e //TODO log instead (#7)
    }
}

private fun parseCallbackData(callbackData: String): CallbackPayload {
    val callbackDataSplitted = callbackData.split("#")
    return CallbackPayload(callbackDataSplitted[0], callbackDataSplitted[1].toInt())
}

private fun getChatId(msg: Message?): Long = msg?.chat?.id ?: Long.MIN_VALUE

private fun getMessageId(msg: Message?): Long? = msg?.messageId

private fun getConversationStorage(): Cache<Long, Conversation> {
    val cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build()
    cacheManager.init()

    return cacheManager.createCache(
        "conversations", CacheConfigurationBuilder.newCacheConfigurationBuilder(
            Long::class.javaObjectType,
            Conversation::class.java,
            ResourcePoolsBuilder.heap(Long.MAX_VALUE)
        )
        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(10)))
    )
}

private fun getConversation(conversations: Cache<Long, Conversation>, chatId: Long): Conversation? {
    val conversation = conversations[chatId]
    if (conversation != null) {
        if (conversation.isFinished()) {
            conversations.remove(chatId)
        } else {
            return conversation
        }
    }
    return null
}

