package org.jetbrains.plugins.ruby.ruby.actions

import junit.framework.Assert
import junit.framework.TestCase
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoRow
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable
import java.nio.file.Paths
import java.util.*

class ImportExportTests : TestCase() {

    fun testSimpleExport() {
        val data = (0 until 2 * CHUNK_SIZE + 1).map {
            createCallInfo("A$it", "foo", listOf("String", "Symbol"), "Integer")
        }

        DatabaseProvider.connectToDB(generateTempDBFilePath(), isDefaultDatabase = true)

        DatabaseProvider.createAllDatabases(DatabaseProvider.defaultDatabase)
        DatabaseProvider.defaultDatabaseTransaction {
            data.forEach { CallInfoTable.insertInfoIfNotContains(it) }
        }

        val exportedDB = generateTempDBFilePath().let { pathToExport: String ->
            ExportContractsAction.exportContractsToFile(pathToExport, moveProgressBar = false)

            return@let DatabaseProvider.connectToDB(pathToExport)
        }

        Assert.assertEquals(DatabaseProvider.defaultDatabase!!.allCallInfos, exportedDB.allCallInfos)
    }

    fun testSimpleImport() {
        val data = (0 until 2 * CHUNK_SIZE + 1).map {
            createCallInfo("A$it", "foo", listOf("String", "Symbol"), "Integer")
        }

        DatabaseProvider.connectToDB(generateTempDBFilePath(), isDefaultDatabase = true)
        DatabaseProvider.createAllDatabases(DatabaseProvider.defaultDatabase)

        val dbToImport = generateTempDBFilePath().let { pathToImport: String ->
            val db = DatabaseProvider.connectToDB(pathToImport)

            DatabaseProvider.createAllDatabases(db)

            transaction(db) {
                data.forEach { CallInfoTable.insertInfoIfNotContains(it) }
            }

            ImportContractsAction.importContractsFromFile(pathToImport, moveProgressBar = false)

            return@let db
        }

        Assert.assertEquals(dbToImport.allCallInfos, DatabaseProvider.defaultDatabase!!.allCallInfos)
    }

    fun testImportWhenDefaultDBIsNotEmpty() {
        val data = setOf(
                createCallInfo("A", "foo", listOf("String", "Symbol"), "Integer"),
                createCallInfo("B", "bar", listOf("Integer"), "String"),
                createCallInfo("C", "foobar", listOf("String"), "String")
        )

        val defaultDBData = setOf(
                createCallInfo("A", "foo", listOf("String", "Symbol"), "Integer"),
                createCallInfo("B", "bar", listOf("String"), "String"),
                createCallInfo("D", "baz", listOf("Integer"), "String"),
                createCallInfo("E", "foobar", listOf("String", "Symbol"), "String")
        )

        DatabaseProvider.connectToDB(generateTempDBFilePath(), isDefaultDatabase = true)
        DatabaseProvider.createAllDatabases(DatabaseProvider.defaultDatabase)
        DatabaseProvider.defaultDatabaseTransaction {
            defaultDBData.forEach { CallInfoTable.insertInfoIfNotContains(it) }
        }

        val dbToImport = generateTempDBFilePath().let { pathToImport: String ->
            val db = DatabaseProvider.connectToDB(pathToImport)

            DatabaseProvider.createAllDatabases(db)

            transaction(db) {
                data.forEach { CallInfoTable.insertInfoIfNotContains(it) }
            }

            ImportContractsAction.importContractsFromFile(pathToImport, moveProgressBar = false)

            return@let db
        }

        Assert.assertEquals(dbToImport.allCallInfos.union(defaultDBData), DatabaseProvider.defaultDatabase!!.allCallInfos)
    }

    private val Database.allCallInfos: Set<CallInfo>
        get() = transaction(this) { CallInfoRow.all().map { it.copy() } }.toSet()

    private fun createCallInfo(className: String, methodName: String, unnamedArgsTypes: List<String>, returnType: String): CallInfo {
        val args = unnamedArgsTypes.mapIndexed { index, s -> ArgumentNameAndType(('a' + index).toString(), s) }
        return CallInfoImpl(MethodInfo(ClassInfo(className), methodName, RVisibility.PUBLIC), emptyList(), args, returnType)
    }

    private fun generateTempDBFilePath(prefix: String = ""): String {
        val dirForTempFiles = System.getProperty("java.io.tmpdir")
        return Paths.get(dirForTempFiles, prefix + UUID.randomUUID()).toString() + DatabaseProvider.H2_DB_FILE_EXTENSION
    }
}
