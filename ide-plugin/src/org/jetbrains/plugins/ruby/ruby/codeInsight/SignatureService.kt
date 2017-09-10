package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.ClassInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.GemInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureTable
import org.jetbrains.ruby.runtime.signature.server.SignatureServer


class SignatureService {
    init {
        Database.connect("jdbc:mysql://localhost:3306/" + "ruby_type_contracts" + "?serverTimezone=UTC&nullNamePatternMatchesAll=true&useSSL=false",
                driver = "com.mysql.cj.jdbc.Driver",
                user = System.getProperty("mysql.user.name", "rubymine"),
                password = System.getProperty("mysql.user.password", "rubymine"))
        transaction {
            SchemaUtils.create(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable)
        }

        Thread {
            val server = SignatureServer
            while (true) {
                try {
                    server.runServer()
                } catch (e: Exception) {
                    LOGGER.error(e)
                }

            }

        }.start()
    }

    companion object {
        private val LOGGER = Logger.getInstance("SignatureService")
    }

}