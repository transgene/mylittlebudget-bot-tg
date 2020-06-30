package net.transgene.mylittlebudget.tg.bot.framework

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

data class AppConfig(
    val botToken: String,
    val googleCredentials: String,
    val users: Map<Long, UserConfig>
)

data class UserConfig(
    val chatId: Long,
    val spreadsheetId: String,
    val sheetId: String,
    val sheetLayout: SheetLayout
)

data class SheetLayout(
    val categoriesColumnName: String,
    val currentMonthColumnName: String,
    val nonRecurringExpenseGroups: List<SheetGroup>,
    val recurringExpenseGroups: List<SheetGroup>,
    val savingsExpenseGroups: List<SheetGroup>,
    val incomeGroups: List<SheetGroup>
)

data class SheetGroup(
    val leadIndex: Long,
    val firstIndex: Long,
    val lastIndex: Long
)

fun loadAppConfig(baseConfigDirectory: Path): AppConfig {
    val configFile = Paths.get(baseConfigDirectory.toString(), "config.toml")
    val config = Toml.parse(configFile)
    if (config.hasErrors()) {
        throw IllegalStateException("Configuration from ${baseConfigDirectory.toAbsolutePath()} loaded with errors: ${config.errors()}")
    }
    fun getString(dottedKey: String) = getStringFromConfig(dottedKey, config, configFile)

    val botToken = getString("bot-token")
    val googleCredentialsFilePath = getString("google-credentials-path")
    val usersConfigDirPath = getString("users-config-dir-path")

    val userConfigs = Files.walk(Paths.get(usersConfigDirPath), 2)
        .filter { it.toString().endsWith(".toml") && Files.isRegularFile(it) }
        .map { loadUserConfig(it) }
        .toList()

    return AppConfig(
        botToken = botToken,
        googleCredentials = Files.readString(Paths.get(googleCredentialsFilePath)),
        users = userConfigs.associateBy { it.chatId }
    )
}

private fun loadUserConfig(userFile: Path): UserConfig {
    val config = Toml.parse(userFile)
    if (config.hasErrors()) {
        throw IllegalStateException("User configuration from ${userFile.toAbsolutePath()} loaded with errors: ${config.errors()}")
    }
    fun <T> getOrFail(dottedKey: String, getFunc: (String) -> T?) = getFromConfigOrFail(dottedKey, userFile, getFunc)
    fun getString(dottedKey: String, configTable: TomlTable) = getStringFromConfig(dottedKey, configTable, userFile)

    val user = getOrFail("user", config::getTable)
    val chatId = getOrFail("chat-id", user::getLong)
    val spreadsheetId = getString("spreadsheet-id", user)
    val sheetId = getString("sheet-id", user)

    val layout = getOrFail("layout", config::getTable)
    val catColumnName = getString("categories-column-name", layout)
    val curMonthColumnName = getString("current-month-column-name", layout)

    val expenseLayout = getOrFail("expense", layout::getTable)
    val nonRecurringExpenses = getOrFail("non-recurring-groups", expenseLayout::getArray)
    val recurringExpenses = getOrFail("recurring-groups", expenseLayout::getArray)
    val savingExpenses = getOrFail("savings-groups", expenseLayout::getArray)

    val incomeLayout = getOrFail("income", layout::getTable)
    val incomes = getOrFail("income-groups", incomeLayout::getArray)

    return UserConfig(
        chatId = chatId,
        spreadsheetId = spreadsheetId,
        sheetId = sheetId,
        sheetLayout = SheetLayout(
            categoriesColumnName = catColumnName,
            currentMonthColumnName = curMonthColumnName,
            nonRecurringExpenseGroups = convertSheetGroups(nonRecurringExpenses, userFile),
            recurringExpenseGroups = convertSheetGroups(recurringExpenses, userFile),
            savingsExpenseGroups = convertSheetGroups(savingExpenses, userFile),
            incomeGroups = convertSheetGroups(incomes, userFile)
        )
    )
}

private fun convertSheetGroups(arr: TomlArray, userFile: Path): List<SheetGroup> {
    fun getLong(dottedKey: String, group: TomlTable) = getFromConfigOrFail(dottedKey, userFile, group::getLong)
    val sheetGroups = mutableListOf<SheetGroup>()
    var i = 0
    while (i < arr.size()) {
        val group = arr.getTable(i)
        val leadIndex = getLong("lead-index", group)
        val firstIndex = getLong("first-index", group)
        val lastIndex = getLong("last-index", group)
        if (leadIndex >= firstIndex) {
            throw IllegalArgumentException("Lead index must be less than first index. File ${userFile.toAbsolutePath()}, ${arr.inputPositionOf(i)}")
        }
        if (lastIndex < firstIndex) {
            throw IllegalArgumentException("First index must be less or equal than last index. File ${userFile.toAbsolutePath()}, ${arr.inputPositionOf(i)}")
        }
        sheetGroups.add(SheetGroup(leadIndex, firstIndex, lastIndex))

        i++
    }
    return sheetGroups
}

private fun getStringFromConfig(dottedKey: String, configTable: TomlTable, configFile: Path): String {
    val value = getFromConfigOrFail(dottedKey, configFile, configTable::getString)
    return value.ifEmpty { throw IllegalArgumentException() }
}

private fun <T> getFromConfigOrFail(dottedKey: String, configPath: Path, getFunc: (String) -> T?): T {
    return getFunc(dottedKey)
        ?: throw IllegalArgumentException("Property '$dottedKey' not found in ${configPath.toAbsolutePath()}")
}

