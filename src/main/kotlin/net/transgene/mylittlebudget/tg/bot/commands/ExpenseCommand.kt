package net.transgene.mylittlebudget.tg.bot.commands

import net.transgene.mylittlebudget.tg.bot.sheets.Cell
import net.transgene.mylittlebudget.tg.bot.sheets.SheetsService

class ExpenseCommand(private val sheetsService: SheetsService) : ExpenseIncomeCommand(sheetsService) {

    override fun getGroups(): List<Cell> = sheetsService.getExpenseGroups()

    override fun getFirstOperationInMonthMessage(amount: Long): String =
        "Поздравляю! Вы потратили $amount рублей на \"${chosenCategoryCell!!.name}\"."

    override fun getOperationMessage(amount: Long): String =
        "Поздравляю! Вы потратили еще $amount рублей на \"${chosenCategoryCell!!.name}\"."
}
