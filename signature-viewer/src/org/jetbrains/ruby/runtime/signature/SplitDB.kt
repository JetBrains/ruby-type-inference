package org.jetbrains.ruby.runtime.signature

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoRow
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable
import java.nio.file.Paths

val gemToDBMap = HashMap<GemInfo?, Database>()

fun gemToDB(info: GemInfo?, outputDir: String, rubyVersion: String): Database =
        gemToDBMap[info] ?: DatabaseProvider.connectToDB(Paths.get(outputDir,
                "${info?.name?.plus("-")?.plus(info.version) ?: "no_gem"}-ruby-$rubyVersion").toString())
                .also {
                    DatabaseProvider.createAllDatabases(it)
                    gemToDBMap[info] = it
                }

fun input(msg: String): String {
    println(msg)
    return readLine()!!
}

/**
 * This small script splits massive database into small databases. Each
 * small database is responsible for particular gem and named accordingly
 */
fun main(args: Array<String>) {
    val dpPath = parseDBViewerCommandLineArgs(args)
    val input = DatabaseProvider.connectToDB(dpPath)

    val outputDir = input("Enter output dir: ")
    val rubyVersion = input("Enter ruby version: ")

    transaction(input) {
        CallInfoRow.all().forEach {
            val callInfo = it.copy()
            transaction(gemToDB(callInfo.methodInfo.classInfo.gemInfo, outputDir, rubyVersion)) {
                CallInfoTable.insertInfoIfNotContains(callInfo)
            }
        }
    }
}
