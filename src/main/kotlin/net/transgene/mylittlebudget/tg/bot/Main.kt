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
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun main(args: Array<String>) {
    val categoryColumn = "A"
    val currentMonthColumn = "B"
    val nonRecurringExpenseGroups: Map<Int, IntRange> =
        mapOf(
            Pair(2, IntRange(4, 7)),
            Pair(8, IntRange(10, 11)),
            Pair(12, IntRange(13, 20)),
            Pair(21, IntRange(23, 24)),
            Pair(25, IntRange(26, 31))
        )
    val recurringExpenseGroups: Map<Int, IntRange> =
        mapOf(
            Pair(34, IntRange(36, 38)),
            Pair(39, IntRange(41, 42)),
            Pair(43, IntRange(45, 46)),
            Pair(52, IntRange(54, 59))
        )
    val savingsExpenseGroups: Map<Int, IntRange> =
        mapOf(
            Pair(34, IntRange(36, 38)),
            Pair(39, IntRange(41, 42)),
            Pair(43, IntRange(45, 46)),
            Pair(52, IntRange(54, 59))
        )
    val allExpenseGroups = nonRecurringExpenseGroups + recurringExpenseGroups + savingsExpenseGroups
    val incomeCategories = Pair(65, 69)

    val bot = bot {
        token = args[0]
        val chatId: Long = args[1].toLong()
        val spreadsheetId = args[2]
        val sheetId = "Бюджет"
        val itemIndex = AtomicInteger(Int.MIN_VALUE)
        val itemName = AtomicReference<String>()

        dispatch {
            message(Filter.Text) { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@message
                }
                if (itemIndex.get() < 0) {
                    bot.sendMessage(chatId = chatId, text = "Простите, не могу понять, к какой операции это относится.")
                    return@message
                }
                val msgText = update.message?.text?.trim() ?: return@message
                try {
                    val amount = msgText.toLong()
                    if (amount < 0) {
                        bot.sendMessage(
                            chatId = chatId,
                            text = "Простите, но пока что я не поддерживаю отрицательные суммы.\nПопробуйте еще раз, пожалуйста."
                        )
                        return@message
                    } else if (amount == 0L) {
                        bot.sendMessage(chatId = chatId, text = "Простите, но 0 я записывать не буду.\nПопробуйте еще раз, пожалуйста.")
                        return@message
                    }
                    val rangeTemplate = "%s!%s%s"
                    val cellRange =
                        rangeTemplate.format(sheetId, currentMonthColumn, itemIndex.get())
                    val cellValueRange: ValueRange =
                        getSheetsService().spreadsheets().values().get(spreadsheetId, cellRange)
                            .setValueRenderOption("FORMULA")
                            .execute()
                    val valueWrapper: List<List<Any>>? = cellValueRange.getValues()
                    val cellValue = valueWrapper?.first()?.first()?.toString()
                    val toBeInserted = when {
                        cellValue.isNullOrBlank() -> "=$amount"
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
                        text = "Поздравляю! Вы потратили еще $amount рублей на \"${itemName.get()}\"."
                    )
                } catch (e: NumberFormatException) {
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Кажется, это не число.\nПопробуйте еще раз, пожалуйста."
                    )
                }
            }
            command("start") { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@command
                }
                //TODO implement getting spreadsheetId and sheetId
            }
            command("exp") { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@command
                }
                itemIndex.set(Int.MIN_VALUE)
                val sheetsService = getSheetsService()
                val groupRangeTemplate = "%s!%s%s"
                val expenseGroupRanges: List<String> =
                    allExpenseGroups.keys.map { groupCell ->
                        groupRangeTemplate.format(
                            sheetId,
                            categoryColumn,
                            groupCell
                        )
                    }

                val groupCategoriesRangeTemplate = "%s!%s%s:%s%s"
                val expenseCategoriesRanges: List<String> = allExpenseGroups.values.map { categoryRange ->
                    groupCategoriesRangeTemplate.format(
                        sheetId,
                        categoryColumn,
                        categoryRange.first,
                        categoryColumn,
                        categoryRange.last
                    )
                }

                val response =
                    sheetsService.spreadsheets().values().batchGet(spreadsheetId)
                        .setRanges(expenseGroupRanges + expenseCategoriesRanges)
                        .setMajorDimension("COLUMNS")
                        .execute()

                val categoriesResponse = response.valueRanges.drop(expenseGroupRanges.size)
                val categoryNames: List<String> = categoriesResponse.map {
                    val catList = it.getValues().first() as List<String>
                    catList.joinToString(prefix = "(", postfix = ")", limit = 3) {
                        it.trimStart().take(5).trimEnd()
//                        val name = it.trim()
//                        if (name.length > 5 && !name[5].isWhitespace()) name.take(5) + "." else name.take(5)
                    }
                }

                val groupsResponse = response.valueRanges.subList(0, expenseGroupRanges.size)
                val groups: List<Pair<String, Int>> = groupsResponse.mapIndexed { i, valRange ->
                    Pair(
                        "${(valRange.getValues().first().first() as String).trim()} ${categoryNames[i]}",
                        valRange.range.replaceBefore("!", "").drop(1).removePrefix(categoryColumn).toInt()
                    )
                }

                val groupButtons: List<List<InlineKeyboardButton>> =
                    groups.map {
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
                    replyMarkup = InlineKeyboardMarkup(groupButtons)
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
                val callbackInfo: List<String> = callbackId.split(delimiters = *arrayOf("."), limit = 4)
                val commandName = callbackInfo[0]
                val subCommandName = callbackInfo[1]
                val payload = callbackInfo[2]
                if (commandName == "exp") {
                    if (subCommandName == "category") {
                        val sheetsService = getSheetsService()
                        val rangeTemplate = "%s!%s%s:%s%s"
                        val categoryRange = allExpenseGroups[payload.toInt()] ?: return@callbackQuery
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

                        val catRangeItr = categoryRange.iterator()
                        val catItems: List<Pair<String, Int>> =
                            response.getValues().first()
                                .map { Pair(it as String, catRangeItr.nextInt()) }
                        val catItemButtons: List<List<InlineKeyboardButton>> = catItems.map {
                            listOf(
                                InlineKeyboardButton(
                                    text = it.first,
                                    callbackData = "exp.item.${it.second}.${it.first}"
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
                        itemIndex.set(payload.toInt())
                        itemName.set(callbackInfo[3])
                        bot.editMessageText(
                            chatId = chatId,
                            messageId = update.callbackQuery?.message?.messageId,
                            text = "Пожалуйста, введите сумму.\nИли нажмите кнопку, и я завершу операцию.",
                            replyMarkup = InlineKeyboardMarkup.createSingleButton(
                                InlineKeyboardButton(
                                    text = "Отменить",
                                    callbackData = "exp.cancel.none"
                                )
                            )
                        )
                    } else if (subCommandName == "cancel") {
                        bot.deleteMessage(
                            chatId = chatId,
                            messageId = update.callbackQuery?.message?.messageId
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
    val credsJson = FileInputStream("./google-api-credentials.json")
    return HttpCredentialsAdapter(
        GoogleCredentials.fromStream(credsJson).createScoped(SheetsScopes.SPREADSHEETS)
    )
}

