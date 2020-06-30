package net.transgene.mylittlebudget.tg.bot.commands

import net.transgene.mylittlebudget.tg.bot.commands.ButtonAction.*
import net.transgene.mylittlebudget.tg.bot.framework.*
import net.transgene.mylittlebudget.tg.bot.sheets.Cell
import net.transgene.mylittlebudget.tg.bot.sheets.SheetsService

class ExpenseCommand(private val sheetsService: SheetsService) : TextCommand, ButtonPressCommand<ButtonPayload> {

    private var chosenCategoryCell: Cell? = null

    override fun call(callMessageId: Long): List<Action> {
        val expenseGroups = sheetsService.getExpenseGroups()
        return if (expenseGroups.size == 1) {
            val categories = sheetsService.getCategoriesInGroup(expenseGroups.first())
            val itemButtons = categories.map { Button(it.name, ButtonPayload(WAIT_FOR_EXPENSE_INPUT, it)) }
            listOf(SendMessage("Выберите категорию:", itemButtons))
        } else {
            val groupButtons = expenseGroups.map { Button(it.name, ButtonPayload(GET_CATEGORIES_IN_GROUP, it)) }
            listOf(SendMessage("Выберите группу:", groupButtons))
        }
    }

    override fun consumeButtonPress(messageId: Long, payload: ButtonPayload): List<Action> {
        if (payload.action != CANCEL_OPERATION && payload.chosenCell == null) {
            throw IllegalStateException("Cell must be selected for $WAIT_FOR_EXPENSE_INPUT action")
        }
        return when (payload.action) {
            GET_CATEGORIES_IN_GROUP -> getCategoriesInGroupAction(payload.chosenCell!!, messageId)
            WAIT_FOR_EXPENSE_INPUT -> {
                chosenCategoryCell = payload.chosenCell
                waitForExpenseInputAction(messageId)
            }
            CANCEL_OPERATION -> cancelOperationAction(messageId)
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

        val isFirstExpenseInMonth = sheetsService.addToCategory(chosenCategoryCell!!, amount)
        val resultMessage = if (isFirstExpenseInMonth) {
            "Поздравляю! Вы потратили $amount рублей на \"${chosenCategoryCell!!.name}\"."
        } else {
            "Поздравляю! Вы потратили еще $amount рублей на \"${chosenCategoryCell!!.name}\"."
        }
        return listOf(SendMessage(resultMessage), Finish)
    }

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
        val itemButtons = categories.map { Button(it.name, ButtonPayload(WAIT_FOR_EXPENSE_INPUT, it)) }
        return listOf(EditMessage(messageId, "Теперь - категорию:", itemButtons))
    }

    private fun waitForExpenseInputAction(messageId: Long): List<Action> {
        return listOf(
            EditMessage(
                messageId,
                "Пожалуйста, введите сумму.\nИли нажмите кнопку, и я завершу операцию.",
                listOf(Button("Отменить", ButtonPayload(CANCEL_OPERATION)))
            )
        )
    }

    private fun cancelOperationAction(messageId: Long): List<Action> {
        return listOf(DeleteMessage(messageId), Finish)
    }
}

data class ButtonPayload(val action: ButtonAction, val chosenCell: Cell? = null)

enum class ButtonAction {
    GET_CATEGORIES_IN_GROUP,
    WAIT_FOR_EXPENSE_INPUT,
    CANCEL_OPERATION
}
