package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.ClassInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.GemInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureTable
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.io.File
import java.io.IOException


class SignatureService {
    init {
        val dbDir = FileUtil.join(PathManager.getSystemPath(), "ruby_runtime_stat")
        val isCreated = File(dbDir).mkdir()
        if (isCreated) {
            LOGGER.info("Created dir for db: " + dbDir)
        }

        if (!File(dbDir).exists()) {
            throw RuntimeException(IOException("Dir for the db does not exist and could not be created: " + dbDir))
        }

        Database.connect("jdbc:mysql://localhost:3306/" + "ruby_type_contracts" + "?serverTimezone=UTC&nullNamePatternMatchesAll=true",
                driver = "com.mysql.cj.jdbc.Driver", user = "rubymine", password = "rubymine")
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