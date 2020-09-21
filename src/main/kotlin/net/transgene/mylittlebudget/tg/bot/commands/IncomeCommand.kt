package net.transgene.mylittlebudget.tg.bot.commands

import net.transgene.mylittlebudget.tg.bot.sheets.Cell
import net.transgene.mylittlebudget.tg.bot.sheets.SheetsService

class IncomeCommand(private val sheetsService: SheetsService) : ExpenseIncomeCommand(sheetsService) {

    override fun getGroups(): List<Cell> = sheetsService.getIncomeGroups()

    override fun getFirstOperationInMonthMessage(amount: Long): String =
        "Поздравляю! Вы получили $amount рублей в категории \"${chosenCategoryCell!!.name}\"."

    override fun getOperationMessage(amount: Long): String =
        "Поздравляю! Вы получили еще $amount рублей в категории \"${chosenCategoryCell!!.name}\"."
}
