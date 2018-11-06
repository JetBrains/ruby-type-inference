package org.jetbrains.ruby.runtime.signature

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoRow
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable

/**
 * Just prints content of [CallInfoTable]
 */
fun main(args: Array<String>) {
    val dpPath = parseDBViewerCommandLineArgs(args)
    DatabaseProvider.connectToDB(dpPath)

    transaction {
        val table = CallInfoRow.all().map { it.copy() }
        table.forEach {
            println("" +
                    (it.methodInfo.classInfo.gemInfo?.name ?: "No gem") + " " +
                    (it.methodInfo.classInfo.gemInfo?.version ?: "No gem") + " " +
                    it.methodInfo.location?.path + " " +
                    it.methodInfo.location?.lineno + " " +
                    it.methodInfo.visibility + " " +
                    it.methodInfo.classInfo.classFQN + " " +
                    it.methodInfo.name + " " +
                    "args:${it.unnamedArgumentsTypesJoinToRawString()} " +
                    "return:${it.returnType}")
        }
        println("Size: ${table.size}")
    }
}

fun parseDBViewerCommandLineArgs(args: Array<String>): String {
    if (args.size != 1) {
        println("Usage: <db-file-path>")
        System.exit(1)
    }
    return args.single()
}
