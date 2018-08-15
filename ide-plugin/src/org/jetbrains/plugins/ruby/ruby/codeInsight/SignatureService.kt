package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.plugins.ruby.ruby.persistent.TypeInferenceDirectory
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.ClassInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.GemInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureTable
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.io.File


class SignatureService {
    init {
        DatabaseProvider.connect(filePath = File(TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toFile(), "ruby-type-inference").absolutePath)
        transaction {
            SchemaUtils.create(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable)
        }

        Thread {
            while (true) {
                try {
                    SignatureServer.runServer()
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