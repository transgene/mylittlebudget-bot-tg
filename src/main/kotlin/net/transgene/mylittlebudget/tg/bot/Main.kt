package net.transgene.mylittlebudget.tg.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.network.Response
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.UpdateCellsRequest
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val categoryColumn = "A"
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
                }
                val msgText = update.message?.text?.trim() ?: return@message
                try {
                    val amount = msgText.toLong()
                    //TODO check that it's not negative
//                    getSheetsService().spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().requests = listOf(
//                        Request().updateCells = UpdateCellsRequest().
//                    ))
                    //TODO update cell in the sheet
                } catch (e: NumberFormatException) {
                    bot.sendMessage(chatId = chatId, text = "Это не похоже на число. Попробуй еще раз.")
                }
            }
            command("start") { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@command
                }
                //TODO implement getting spreadsheetId and sheetId
            }
            command("clear") { bot, update ->
                itemIndex.set(Int.MIN_VALUE)
                bot.sendMessage(chatId = chatId, text = "Всё, проехали")
            }
            command("exp") { bot, update ->
                if (getChatId(update.message) != chatId) {
                    return@command
                }
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
                            it.range.replaceBefore("!", "").drop(1).removePrefix(categoryColumn).toInt()
                        )
                    }
                val categoryButtons: List<List<InlineKeyboardButton>> =
                    categories.map {
                        listOf(InlineKeyboardButton(text = it.first, callbackData = "exp.category.${it.second}"))
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
                            response.getValues().first().map { Pair(it as String, catRangeItr.nextInt()) }
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

    return Sheets.Builder(transport, JacksonFactory.getDefaultInstance(), getCredentials(transport))
        .setApplicationName("wubwubwub").build()
}

private fun getChatId(msg: Message?): Long = msg?.chat?.id ?: Long.MIN_VALUE

fun getCredentials(httpTransport: NetHttpTransport): Credential {
    val credsJson = NetHttpTransport::class.java.getResourceAsStream("/credentials.json")
    val jsonFactory = JacksonFactory.getDefaultInstance()
    val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(credsJson))
    val flow = GoogleAuthorizationCodeFlow.Builder(
        httpTransport,
        jsonFactory,
        clientSecrets,
        listOf(SheetsScopes.SPREADSHEETS)
    )
        .setDataStoreFactory(
            FileDataStoreFactory(
                File("tokens")
            )
        ).build()
    val receiver = LocalServerReceiver.Builder().setPort(8888).build()
    return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
}
