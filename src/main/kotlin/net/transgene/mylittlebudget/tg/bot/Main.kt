package net.transgene.mylittlebudget.tg.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
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
import java.io.File
import java.io.InputStreamReader

fun main(args: Array<String>) {
    //TODO 1) move column id to a separate val; 2) change pairs to int ranges
    val expenseCategoriesMap: Map<String, Pair<String, String>> =
        mapOf(
            Pair("A2", Pair("A4", "A7")),
            Pair("A8", Pair("A10", "A11")),
            Pair("A12", Pair("A13", "A20")),
            Pair("A21", Pair("A23", "A24")),
            Pair("A25", Pair("A26", "A31")),
            Pair("A34", Pair("A36", "A38")),
            Pair("A39", Pair("A41", "A42")),
            Pair("A43", Pair("A45", "A46")),
            Pair("A52", Pair("A54", "A59"))
        )
    val incomeCategories = Pair("A65", "A69")

    val bot = bot {
        token = args[0]
        val chatId: Long = args[1].toLong()
        val spreadsheetId = args[2]
        val sheetId = "Бюджет"

        dispatch {
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
                val sheetsService = getSheetsService()
                val rangeTemplate = "%s!%s"
                val categoryRanges: List<String> =
                    expenseCategoriesMap.keys.map { categoryCell -> rangeTemplate.format(sheetId, categoryCell) }
                val response =
                    sheetsService.spreadsheets().values().batchGet(spreadsheetId)
                        .setRanges(categoryRanges).execute()
                val categories: List<Pair<String, String>> =
                    response.valueRanges.map {
                        Pair(
                            it.getValues().first().first(),
                            it.range.replaceBefore("!", "").drop(1)
                        )
                    } as List<Pair<String, String>>
                val categoryButtons: List<List<InlineKeyboardButton>> =
                    categories.map {
                        listOf(InlineKeyboardButton(text = it.first, callbackData = "exp.category.${it.second}"))
                    }

                bot.sendMessage(
                    chatId = chatId,
                    text = "Choose a category",
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
                val payload = callbackInfo[2]
                if (commandName == "exp") {
                    if (subCommandName == "category") {
                        val sheetsService = getSheetsService()
                        val rangeTemplate = "%s!%s:%s"
                        val (rangeStart, rangeEnd) = expenseCategoriesMap[payload] ?: return@callbackQuery
                        val response =
                            sheetsService.spreadsheets().values()
                                .get(spreadsheetId, rangeTemplate.format(sheetId, rangeStart, rangeEnd))
                                .setMajorDimension("COLUMNS").execute()

                        val catItems: List<Pair<String, String>> =
                            response.getValues().first().map { Pair(it as String, "A...") }
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
                            text = "Choose an item",
                            replyMarkup = InlineKeyboardMarkup(catItemButtons)
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

fun generateButtons(): List<List<InlineKeyboardButton>> {
    return listOf(
        listOf(
            InlineKeyboardButton(text = "\uD83D\uDE1BЗайти", callbackData = "testButton"),
            InlineKeyboardButton(text = "\uD83D\uDCA8Выбраться", callbackData = "showAlert")
        )
    )
}

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
