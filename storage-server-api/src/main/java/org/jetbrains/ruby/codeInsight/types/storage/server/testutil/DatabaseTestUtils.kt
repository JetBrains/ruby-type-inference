package org.jetbrains.ruby.codeInsight.types.storage.server.testutil

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider

/**
 * This function is used to test database related things. Just creates new clean databases,
 * executes [block] and remove created databases.
 *
 * [TestCase.setUp] and [TestCase.tearDown] functions won't help because [DatabaseProvider.createAllDatabases]
 * must be called in the same [transaction] block for in memory database
 */
fun doDBTest(block: () -> Unit) {
    transaction {
        DatabaseProvider.createAllDatabases()
        block()
        DatabaseProvider.dropAllDatabases()
    }
}
