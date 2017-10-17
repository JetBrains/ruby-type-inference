package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

object DatabaseProvider {
    private val IN_MEMORY_URL = "jdbc:h2:mem:test"
    private val IN_MEMORY_DRIVER = "org.h2.Driver"
    private val MYSQL_URL = System.getProperty("mysql.url",
            "jdbc:mysql://localhost:3306/" +
                    "ruby_type_contracts" +
                    "?serverTimezone=UTC&nullNamePatternMatchesAll=true&useSSL=false")
    private val MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver"
    private val MYSQL_USER = System.getProperty("mysql.user.name", "rubymine")
    private val MYSQL_PASSWORD = System.getProperty("mysql.user.password", "rubymine")


    fun connect(inMemory: Boolean = false,
                setupConnection: ((Connection) -> Unit)? = null,
                manager: ((Database) -> TransactionManager)? = null) {
        if (inMemory) {
            if (setupConnection == null && manager == null) {
                Database.connect(IN_MEMORY_URL, driver = IN_MEMORY_DRIVER)
            } else if (manager == null) {
                Database.connect(IN_MEMORY_URL, driver = IN_MEMORY_DRIVER, setupConnection = setupConnection!!)
            } else if (setupConnection == null) {
                Database.connect(IN_MEMORY_URL, driver = IN_MEMORY_DRIVER, manager = manager)
            } else {
                Database.connect(IN_MEMORY_URL, driver = IN_MEMORY_DRIVER,
                        setupConnection = setupConnection, manager = manager)
            }
        } else {
            if (setupConnection == null && manager == null) {
                Database.connect(MYSQL_URL, driver = MYSQL_DRIVER,
                        user = MYSQL_USER,
                        password = MYSQL_PASSWORD)
            } else if (manager == null) {
                Database.connect(MYSQL_URL, driver = MYSQL_DRIVER,
                        user = MYSQL_USER,
                        password = MYSQL_PASSWORD,
                        setupConnection = setupConnection!!)
            } else if (setupConnection == null) {
                Database.connect(MYSQL_URL, driver = MYSQL_DRIVER,
                        user = MYSQL_USER,
                        password = MYSQL_PASSWORD,
                        manager = manager)
            } else {
                Database.connect(MYSQL_URL, driver = MYSQL_DRIVER,
                        user = MYSQL_USER,
                        password = MYSQL_PASSWORD,
                        setupConnection = setupConnection,
                        manager = manager)
            }
        }
    }
}