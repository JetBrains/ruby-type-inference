package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.*

object DatabaseProvider {
    private const val IN_MEMORY_URL = "jdbc:h2:mem:test"
    private const val H2_DRIVER = "org.h2.Driver"
    private const val H2_DB_FILE_EXTENSION = ".mv.db"

    @JvmStatic
    fun connectToInMemoryDB() {
        Database.connect(IN_MEMORY_URL, driver = H2_DRIVER)
    }

    @JvmStatic
    fun connectToDB(filePath: String): Database {
        val fixedFilePath = if (filePath.endsWith(H2_DB_FILE_EXTENSION)) {
            filePath.substring(0, filePath.lastIndexOf(H2_DB_FILE_EXTENSION))
        } else {
            filePath
        }
        return Database.connect("jdbc:h2:$fixedFilePath", driver = H2_DRIVER)
    }

    @JvmOverloads
    fun createAllDatabases(db: Database? = null) {
        transaction(db) {
            SchemaUtils.create(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable, CallInfoTable)
        }
    }

    @JvmOverloads
    fun dropAllDatabases(db: Database? = null) {
        transaction(db) {
            SchemaUtils.drop(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable, CallInfoTable)
        }
    }
}