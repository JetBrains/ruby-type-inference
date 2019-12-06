package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.Logger
import org.jetbrains.ruby.codeInsight.injector
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.*

object DatabaseProvider {
    var defaultDatabase: Database? = null
        private set
    /**
     * Default database file path with .mv.db suffix included
     */
    var defaultDatabaseFilePath: String? = null
        private set
    private const val IN_MEMORY_URL = "jdbc:h2:mem:test"
    private const val H2_DRIVER = "org.h2.Driver"
    const val H2_DB_FILE_EXTENSION = ".mv.db"
    private val logger: Logger = injector.getLogger(DatabaseProvider::class.java)

    @JvmStatic
    fun connectToInMemoryDB(isDefaultDatabase: Boolean = false): Database {
        val database = Database.connect(IN_MEMORY_URL, driver = H2_DRIVER)
        if (isDefaultDatabase) {
            defaultDatabase = database
        }
        logger.info("Connected to in memory DB")
        logDatabaseSize(database)
        return database
    }

    @JvmStatic
    fun connectToDB(filePath: String, isDefaultDatabase: Boolean = false): Database {
        check(filePath.endsWith(H2_DB_FILE_EXTENSION)) {
            "File path must end with $H2_DB_FILE_EXTENSION suffix"
        }
        val filePathForUrl = filePath.substring(0, filePath.lastIndexOf(H2_DB_FILE_EXTENSION))
        val database = Database.connect("jdbc:h2:$filePathForUrl", driver = H2_DRIVER)
        if (isDefaultDatabase) {
            defaultDatabase = database
            defaultDatabaseFilePath = filePath
        }
        logger.info("Connected to DB: $filePath")
        createAllDatabases(database)
        logDatabaseSize(database)
        return database
    }

    @JvmStatic
    fun <T> defaultDatabaseTransaction(statement: Transaction.() -> T): T {
        val defaultDatabaseLocal = defaultDatabase ?: throw IllegalStateException("Assign defaultDatabase firstly")
        return transaction(defaultDatabaseLocal, statement)
    }

    @JvmOverloads
    fun createAllDatabases(db: Database? = null) {
        transaction(db ?: defaultDatabase) {
            SchemaUtils.create(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable, CallInfoTable)
        }
    }

    @JvmOverloads
    fun dropAllDatabases(db: Database? = null) {
        transaction(db ?: defaultDatabase) {
            SchemaUtils.drop(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable, CallInfoTable)
        }
    }

    private fun logDatabaseSize(db: Database) {
        transaction(db) {
            for (table in listOf(CallInfoTable, MethodInfoTable, ClassInfoTable, SignatureTable, GemInfoTable)) {
                logger.info("${table.tableName} table's number of rows ${table.selectAll().count()}")
            }
        }
    }
}