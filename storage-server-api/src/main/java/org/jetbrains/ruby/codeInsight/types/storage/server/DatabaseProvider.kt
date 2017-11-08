package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.exposed.sql.Database

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


    fun connect(inMemory: Boolean = false) {
        if (inMemory) {
            Database.connect(IN_MEMORY_URL, driver = IN_MEMORY_DRIVER)
        } else {
            Database.connect(MYSQL_URL, driver = MYSQL_DRIVER,
                    user = MYSQL_USER,
                    password = MYSQL_PASSWORD)
        }
    }
}