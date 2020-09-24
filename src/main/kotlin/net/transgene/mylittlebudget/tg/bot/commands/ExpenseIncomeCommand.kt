package net.transgene.mylittlebudget.tg.bot.commands

import net.transgene.mylittlebudget.tg.bot.commands.ButtonAction.GET_CATEGORIES_IN_GROUP
import net.transgene.mylittlebudget.tg.bot.commands.ButtonAction.WAIT_FOR_AMOUNT_INPUT
import net.transgene.mylittlebudget.tg.bot.framework.*
import net.transgene.mylittlebudget.tg.bot.sheets.Cell
import net.transgene.mylittlebudget.tg.bot.sheets.SheetsService

abstract class ExpenseIncomeCommand(private val sheetsService: SheetsService) : TextCommand, ButtonPressCommand<ButtonPayload> {

    protected var chosenCategoryCell: Cell? = null

    override fun call(callMessageId: Long): List<Action> {
        val groups = getGroups()
        return if (groups.size == 1) {
            val categories = sheetsService.getCategoriesInGroup(groups.first())
            val itemButtons = categories.map { Button(it.name, ButtonPayload(WAIT_FOR_AMOUNT_INPUT, it)) }
            listOf(SendMessage("Выберите категорию:", itemButtons))
        } else {
            val groupButtons = groups.map { Button(it.name, ButtonPayload(GET_CATEGORIES_IN_GROUP, it)) }
            listOf(SendMessage("Выберите группу:", groupButtons))
        }
    }

    override fun consumeButtonPress(messageId: Long, payload: ButtonPayload): List<Action> {
        if (payload.chosenCell == null) {
            throw IllegalStateException("Cell must be selected for $WAIT_FOR_AMOUNT_INPUT action")
        }
        return when (payload.action) {
            GET_CATEGORIES_IN_GROUP -> getCategoriesInGroupAction(payload.chosenCell, messageId)
            WAIT_FOR_AMOUNT_INPUT -> {
                chosenCategoryCell = payload.chosenCell
                waitForAmountInputAction(messageId)
            }
        }
    }

    override fun consumeText(messageId: Long, text: String): List<Action> {
        if (chosenCategoryCell == null) {
            return listOf(SendMessage("Простите, не могу понять, к какой операции это относится."))
        }
        val (amount, error) = convertAndValidate(text)
        if (error != null) {
            return listOf(SendMessage(error))
        }

        val isFirstOperationInMonth = sheetsService.addToCategory(chosenCategoryCell!!, amount)
        val resultMessage = if (isFirstOperationInMonth) {
            getFirstOperationInMonthMessage(amount)
        } else {
            getOperationMessage(amount)
        }
        return listOf(SendMessage(text = resultMessage, cancellable = false), Finish)
    }

    protected abstract fun getGroups(): List<Cell>

    protected abstract fun getFirstOperationInMonthMessage(amount: Long): String

    protected abstract fun getOperationMessage(amount: Long): String

    private fun convertAndValidate(messageText: String): Pair<Long, String?> {
        val trimmedText = messageText.trim()
        var amount: Long = Long.MIN_VALUE
        var error: String? = null
        try {
            amount = trimmedText.toLong()
            if (amount < 0) {
                error = "Простите, но пока что я не поддерживаю отрицательные суммы.\nПопробуйте еще раз, пожалуйста."
            } else if (amount == 0L) {
                error = "Простите, но 0 я записывать не буду.\nПопробуйте еще раз, пожалуйста."
            }
        } catch (e: NumberFormatException) {
            error = "Кажется, это не число.\nПопробуйте еще раз, пожалуйста."
        }
        return Pair(amount, error)
    }

    private fun getCategoriesInGroupAction(groupCell: Cell, messageId: Long): List<Action> {
        val categories = sheetsService.getCategoriesInGroup(groupCell)
        val itemButtons = categories.map { Button(it.name, ButtonPayload(WAIT_FOR_AMOUNT_INPUT, it)) }
        return listOf(EditMessage(messageId, "Теперь - категорию:", itemButtons))
    }

    private fun waitForAmountInputAction(messageId: Long): List<Action> {
        return listOf(EditMessage(messageId, "Пожалуйста, введите сумму:"))
    }
}

data class ButtonPayload(val action: ButtonAction, val chosenCell: Cell? = null)

enum class ButtonAction {
    GET_CATEGORIES_IN_GROUP,
    WAIT_FOR_AMOUNT_INPUT
}
