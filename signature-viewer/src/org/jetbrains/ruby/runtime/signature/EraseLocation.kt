package org.jetbrains.ruby.runtime.signature

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.Location
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable

/**
 * Erases location. This is needed because annotated [MethodInfo]s shouldn't contain any info related to how
 * machine of developer who annotated some lib is configured. But [Location] contains home dir,
 * .rvm or .rbenv folder, e.t.c so it's needed to be erased for annotated libs.
 */
fun main(args: Array<String>) {
    val dpPath = parseDBViewerCommandLineArgs(args)
    DatabaseProvider.connectToDB(dpPath)

    transaction {
        // This is updateAll
        MethodInfoTable.update {
            it[MethodInfoTable.locationFile] = null
        }
    }
}
