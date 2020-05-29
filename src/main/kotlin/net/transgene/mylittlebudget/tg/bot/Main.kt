package net.transgene.mylittlebudget.tg.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val categoryColumn = "A"
    val currentMonthColumn = "B"
    val expenseCategoriesMap: Map<Int, IntRange> =
        mapOf(
            Pair(2, IntRange(4, 7)),
            Pair(8, IntRange(10, 11)),
            Pair(12, IntRange(13, 20)),
            Pair(21, IntRange(23, 24)),
            Pair(25, IntRange(26, 31)),
            Pair(34, IntRange(36, 38)),
            Pair(39, IntRange(41, 42)),
            Pair(43, IntRange(45, 46)),
            Pair(52, IntRange(54, 59))
        )
    val incomeCategories = Pair(65, 69)

    val bot = bot {
        token = args[0]
        val chatId: Long = args[1].toLong()
        val spreadsheetId = args[2]
        val sheetId = "Бюджет"
        val itemIndex = AtomicInteger(Int.MIN_VALUE)

        dispatch {
            message(Filter.Text) { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@message
                }
                if (itemIndex.get() < 0) {
                    bot.sendMessage(chatId = chatId, text = "Непонятно, что это всё значит")
                    return@message
                }
                val msgText = update.message?.text?.trim() ?: return@message
                try {
                    val amount = msgText.toLong()
                    if (amount < 0) {
                        bot.sendMessage(
                            chatId = chatId,
                            text = "Пока что мы не поддерживаем отрицательные величины. \nПопробуй еще раз."
                        )
                        return@message
                    } else if (amount == 0L) {
                        bot.sendMessage(chatId = chatId, text = "Почему ноль? Попробуй еще раз.")
                        return@message
                    }
                    val rangeTemplate = "%s!%s%s"
                    val cellRange =
                        rangeTemplate.format(sheetId, currentMonthColumn, itemIndex.get())
                    val cellValueRange: ValueRange = getSheetsService().spreadsheets().values().get(
                        spreadsheetId, cellRange
                    ).setValueRenderOption("FORMULA").execute()
                    val valueWrapper: List<List<Any>>? = cellValueRange.getValues()
                    val cellValue = valueWrapper?.first()?.first()?.toString()
                    val toBeInserted = when {
                        cellValue == null || cellValue.isBlank() -> "=$amount"
                        !cellValue.startsWith("=") -> "=$cellValue+$amount"
                        else -> "$cellValue+$amount"
                    }
                    getSheetsService().spreadsheets().values().update(
                        spreadsheetId, cellRange,
                        ValueRange().setValues(
                            listOf(listOf(toBeInserted))
                        )
                    ).setValueInputOption("USER_ENTERED").execute()
                    itemIndex.set(Int.MIN_VALUE)
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Поздравляю, ты потратил еще немного денег!"
                    )
                } catch (e: NumberFormatException) {
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Это не похоже на число. Попробуй еще раз."
                    )
                }
            }
            command("start") { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@command
                }
                //TODO implement getting spreadsheetId and sheetId
            }
            command("clear") { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@command
                }
                itemIndex.set(Int.MIN_VALUE)
                bot.sendMessage(chatId = chatId, text = "Всё, проехали")
            }
            command("exp") { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@command
                }
                itemIndex.set(Int.MIN_VALUE)
                val sheetsService = getSheetsService()
                val rangeTemplate = "%s!%s%s"
                val categoryRanges: List<String> =
                    expenseCategoriesMap.keys.map { categoryCell ->
                        rangeTemplate.format(
                            sheetId,
                            categoryColumn,
                            categoryCell
                        )
                    }
                val response =
                    sheetsService.spreadsheets().values().batchGet(spreadsheetId)
                        .setRanges(categoryRanges).execute()
                val categories: List<Pair<String, Int>> =
                    response.valueRanges.map {
                        Pair(
                            it.getValues().first().first() as String,
                            it.range.replaceBefore("!", "").drop(1).removePrefix(categoryColumn)
                                .toInt()
                        )
                    }
                val categoryButtons: List<List<InlineKeyboardButton>> =
                    categories.map {
                        listOf(
                            InlineKeyboardButton(
                                text = it.first,
                                callbackData = "exp.category.${it.second}"
                            )
                        )
                    }

                bot.sendMessage(
                    chatId = chatId,
                    text = "Выберите группу:",
                    replyMarkup = InlineKeyboardMarkup(categoryButtons)
                )
            }

            callbackQuery { bot, update ->
                if (getChatId(update.callbackQuery?.message) != chatId) {
                    return@callbackQuery
                }
                val callbackId = update.callbackQuery?.data
                if (callbackId == null) {
                    return@callbackQuery
                }
                val callbackInfo: List<String> = callbackId.split(".")
                val commandName = callbackInfo[0]
                val subCommandName = callbackInfo[1]
                val payload = callbackInfo[2].toInt()
                if (commandName == "exp") {
                    if (subCommandName == "category") {
                        val sheetsService = getSheetsService()
                        val rangeTemplate = "%s!%s%s:%s%s"
                        val categoryRange = expenseCategoriesMap[payload] ?: return@callbackQuery
                        val response =
                            sheetsService.spreadsheets().values()
                                .get(
                                    spreadsheetId,
                                    rangeTemplate.format(
                                        sheetId,
                                        categoryColumn,
                                        categoryRange.first,
                                        categoryColumn,
                                        categoryRange.last
                                    )
                                )
                                .setMajorDimension("COLUMNS").execute()

//                        val rangeSequence =
//                            generateSequence(categoryRange.first, { i -> i.plus(categoryRange.step) })
                        val catRangeItr = categoryRange.iterator()
                        val catItems: List<Pair<String, Int>> =
                            response.getValues().first()
                                .map { Pair(it as String, catRangeItr.nextInt()) }
                        val catItemButtons: List<List<InlineKeyboardButton>> = catItems.map {
                            listOf(
                                InlineKeyboardButton(
                                    text = it.first,
                                    callbackData = "exp.item.${it.second}"
                                )
                            )
                        }

                        bot.editMessageText(
                            chatId = chatId,
                            messageId = update.callbackQuery?.message?.messageId,
                            text = "Теперь - категорию:",
                            replyMarkup = InlineKeyboardMarkup(catItemButtons)
                        )
                    } else if (subCommandName == "item") {
                        itemIndex.set(payload)
                        bot.editMessageText(
                            chatId = chatId,
                            messageId = update.callbackQuery?.message?.messageId,
                            text = "Введите сумму или отправьте /clear, чтобы сбросить операцию"
                        )
                    }
                }
            }
        }
    }
    bot.startPolling()
}

private fun getSheetsService(): Sheets {
    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val credentials = getCredentials()
    return Sheets.Builder(transport, JacksonFactory.getDefaultInstance(), credentials)
        .setApplicationName("wubwubwub").build()
}

private fun getChatId(msg: Message?): Long = msg?.chat?.id ?: Long.MIN_VALUE

fun getCredentials(): HttpRequestInitializer {
    val credsJson =
        NetHttpTransport::class.java.getResourceAsStream("/service-acc-credentials.json")
    return HttpCredentialsAdapter(
        GoogleCredentials.fromStream(credsJson).createScoped(SheetsScopes.SPREADSHEETS)
    )
}

