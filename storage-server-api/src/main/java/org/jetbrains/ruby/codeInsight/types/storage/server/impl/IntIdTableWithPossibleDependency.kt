package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction

abstract class IntIdTableWithPossibleDependency<in T, out F>(
        private val dependency: IntIdTableWithPossibleDependency<F, *>?) : IntIdTable() {
    fun findRowId(row: T): EntityID<Int>? {
        if (row is IntEntity) {
            return row.id
        }

        return transaction {
            traverseDependencies(row).let {
                it.joinedWithDependencies.select { it.searchCriteria }
            }.firstOrNull()?.get(id)
        }
    }

    open fun insertInfoIfNotContains(info: T): EntityID<Int>? {
        return transaction {
            val dependencyId: EntityID<Int>? = convertInfoToDependencyFormant(info)?.let {
                dependency?.insertInfoIfNotContains(it)
            }
            var ret = findRowId(info)
            if (ret != null) {
                update(where = { id eq ret!! }) { updateStatementBuilder: UpdateStatement ->
                    writeInfoToBuilder(updateStatementBuilder, info, dependencyId)
                }
            } else {
                ret = insertAndGetId { insertStatementBuilder: InsertStatement<EntityID<Int>> ->
                    writeInfoToBuilder(insertStatementBuilder, info, dependencyId)
                }
            }
            removeInvalidInfo(info)
            return@transaction ret
        }
    }

    protected open fun removeInvalidInfo(validInfo: T) { }
    protected abstract fun writeInfoToBuilder(builder: UpdateBuilder<*>, info: T, dependencyId: EntityID<Int>?)
    protected abstract fun SqlExpressionBuilder.createSearchCriteriaForInfo(info: T): Op<Boolean>
    protected abstract fun convertInfoToDependencyFormant(info: T): F?

    private data class TraverseResult(var joinedWithDependencies: ColumnSet, var searchCriteria: Op<Boolean>)
    private fun traverseDependencies(info: T): TraverseResult {
        val searchCriteria = SqlExpressionBuilder.createSearchCriteriaForInfo(info)
        val convertedInfo by lazy { convertInfoToDependencyFormant(info) }
        if (dependency == null || convertedInfo == null) {
            return TraverseResult(this, searchCriteria)
        }

        val ret = dependency.traverseDependencies(convertedInfo)
        ret.joinedWithDependencies = ret.joinedWithDependencies.innerJoin(this)
        ret.searchCriteria = ret.searchCriteria and searchCriteria

        return ret
    }
}

abstract class IntIdTableWithoutDependency<T> : IntIdTableWithPossibleDependency<T, Nothing>(null) {
    final override fun convertInfoToDependencyFormant(info: T): Nothing? = null
}