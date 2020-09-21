package net.transgene.mylittlebudget.tg.bot.sheets

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import net.transgene.mylittlebudget.tg.bot.framework.SheetGroup
import net.transgene.mylittlebudget.tg.bot.framework.UserConfig

class GoogleSheetsService(googleCredentials: String, userInfo: UserConfig) : SheetsService {

    private val singleCellRetrievalTemplate = "%s!%s%d"
    private val cellRangeRetrievalTemplate = "%s!%s%d:%s%d"

    private val spreadsheetId = userInfo.spreadsheetId
    private val sheetId = userInfo.sheetId
    private val sheetsRestService: Sheets

    private val categoriesColumn = userInfo.sheetLayout.categoriesColumnName
    private val currentMonthColumn = userInfo.sheetLayout.currentMonthColumnName

    private val nonRecurringExpenseGroups: Map<Long, LongRange>
    private val recurringExpenseGroups: Map<Long, LongRange>
    private val savingsExpenseGroups: Map<Long, LongRange>
    private val allExpenseGroups: Map<Long, LongRange>

    private val incomeGroups: Map<Long, LongRange>
    private val allGroups: Map<Long, LongRange>

    init {
        val credentials = getCredentials(googleCredentials)
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        sheetsRestService = Sheets.Builder(transport, JacksonFactory.getDefaultInstance(), credentials)
            .setApplicationName("My Little Budget Tg Bot")
            .build()

        fun List<SheetGroup>.convert() = this.associate { it.leadIndex to it.firstIndex..it.lastIndex }
        nonRecurringExpenseGroups = userInfo.sheetLayout.nonRecurringExpenseGroups.convert()
        recurringExpenseGroups = userInfo.sheetLayout.recurringExpenseGroups.convert()
        savingsExpenseGroups = userInfo.sheetLayout.savingsExpenseGroups.convert()
        allExpenseGroups = nonRecurringExpenseGroups + recurringExpenseGroups + savingsExpenseGroups
        incomeGroups = userInfo.sheetLayout.incomeGroups.convert()
        allGroups = allExpenseGroups + incomeGroups
    }

    override fun getExpenseGroups(): List<Cell> = loadGroupsData(allExpenseGroups)

    override fun getIncomeGroups(): List<Cell> = loadGroupsData(incomeGroups)

    override fun getCategoriesInGroup(groupCell: Cell): List<Cell> {
        val categoryIdsRange = allGroups[groupCell.id]
        if (categoryIdsRange == null) {
            throw IllegalArgumentException("Cannot find group with ID ${groupCell.id}")
        }
        val categoryCellRangeDefinition = cellRangeRetrievalTemplate.format(
            sheetId, categoriesColumn, categoryIdsRange.first, categoriesColumn, categoryIdsRange.last
        )

        val restResponse = sheetsRestService.spreadsheets().values()
            .get(spreadsheetId, categoryCellRangeDefinition)
            .setMajorDimension("COLUMNS").execute()

        val categoryNames = restResponse.getValues().first() as List<*>
        return categoryIdsRange.mapIndexed { i, id -> Cell(id, categoryNames[i] as String) }
    }

    override fun addToCategory(categoryCell: Cell, amount: Long): Boolean {
        validateCategory(categoryCell)
        if (amount <= 0) {
            throw IllegalArgumentException("Amount must be positive")
        }

        val cellRange = singleCellRetrievalTemplate.format(sheetId, currentMonthColumn, categoryCell.id)
        val restResponse = sheetsRestService.spreadsheets().values()
            .get(spreadsheetId, cellRange)
            .setValueRenderOption("FORMULA").execute()

        var cellWasEmpty = false
        val cellValue = restResponse.getValues()?.first()?.first()?.toString()
        val toBeInserted = when {
            cellValue.isNullOrBlank() -> {
                cellWasEmpty = true
                "=$amount"
            }
            cellValue.startsWith("=") -> "$cellValue+$amount"
            else -> "=$cellValue+$amount"
        }

        sheetsRestService.spreadsheets().values()
            .update(spreadsheetId, cellRange, ValueRange().setValues(listOf(listOf(toBeInserted))))
            .setValueInputOption("USER_ENTERED").execute()

        return cellWasEmpty
    }

    private fun loadGroupsData(groups: Map<Long, LongRange>): List<Cell> {
        val expenseGroupCellDefinitions: List<String> = groups.keys.map {
            singleCellRetrievalTemplate.format(sheetId, categoriesColumn, it)
        }
        val expenseCategoryCellRangeDefinitions: List<String> = groups.values.map { categoryRange ->
            cellRangeRetrievalTemplate.format(
                sheetId,
                categoriesColumn,
                categoryRange.first,
                categoriesColumn,
                categoryRange.last
            )
        }

        val restResponse = sheetsRestService.spreadsheets().values()
            .batchGet(spreadsheetId)
            .setRanges(expenseGroupCellDefinitions + expenseCategoryCellRangeDefinitions)
            .setMajorDimension("COLUMNS").execute()

        val categoriesResponse = restResponse.valueRanges.drop(expenseGroupCellDefinitions.size)
        val categoryAbbrevs: List<String> = categoriesResponse.map { categoryRange ->
            val catList = categoryRange.getValues().first() as List<String>
            catList.joinToString(prefix = "(", postfix = ")", limit = 3) { it.trimStart().take(5).trimEnd() }
        }

        val groupsResponse = restResponse.valueRanges.subList(0, expenseGroupCellDefinitions.size)
        return groupsResponse.mapIndexed { i, valRange ->
            val groupId = valRange.range.replaceBefore("!", "").drop(1).removePrefix(categoriesColumn).toLong()
            val groupName = (valRange.getValues().first().first() as String).trim()
            Cell(groupId, "$groupName ${categoryAbbrevs[i]}")
        }
    }

    private fun validateCategory(categoryCell: Cell) {
        val rangeForCategoryId = allGroups.values.find { it.contains(categoryCell.id) }
        if (rangeForCategoryId == null) {
            throw IllegalArgumentException("Cannot find category with ID ${categoryCell.id}")
        }
    }

    private fun getCredentials(credentialsString: String): HttpRequestInitializer {
        return HttpCredentialsAdapter(
            GoogleCredentials.fromStream(credentialsString.byteInputStream()).createScoped(SheetsScopes.SPREADSHEETS)
        )
    }
}
