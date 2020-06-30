package net.transgene.mylittlebudget.tg.bot.sheets

interface SheetsService {

    fun getExpenseGroups(): List<Cell>

    fun getIncomeGroups(): List<Cell>

    fun getCategoriesInGroup(groupCell: Cell): List<Cell>

    fun addToCategory(categoryCell: Cell, amount: Long): Boolean
}

data class Cell(val id: Long, val name: String)
