package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Represents SQL table which can possibly have dependency (by having dependency we mean that `this` SQL table can
 * possibly have column where we store id which refers to some row in other table called [dependency])
 * @param dependency SQL table which rows can be possibly referred from `this` SQL table
 * @param T type of info stored in this SQL table
 * @param F type of info stored in [dependency] SQL table
 */
abstract class IntIdTableWithPossibleDependency<in T, out F>(
        private val dependency: IntIdTableWithPossibleDependency<F, *>?) : IntIdTable() {
    /**
     * Find id of row where [info] is located. **Call this function only inside [transaction] block**
     */
    fun findRowId(info: T): EntityID<Int>? {
        if (info is IntEntity) {
            return info.id
        }

        if (!validateInfo(info)) {
            return null
        }

        return traverseDependencies(info).let {
            it.joinedWithDependencies.select { it.searchCriteria }
        }.firstOrNull()?.get(id)
    }

    /**
     * Insert info into SQL table if [info] is not still in the table; otherwise updates information in row according to [info].
     * **Call this function only inside [transaction] block**
     *
     * @return id of row where [info] was inserted or if SQL table already contains [info] than returns id of corresponding row
     */
    open fun insertInfoIfNotContains(info: T): EntityID<Int>? {
        val dependencyId: EntityID<Int>? = convertInfoToDependencyFormant(info)?.let {
            dependency?.insertInfoIfNotContains(it)
        }
        if (!validateInfoBeforeWritingToBuilder(info, dependencyId)) {
            return null
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
        return ret
    }

    protected open fun removeInvalidInfo(validInfo: T) { }
    protected open fun validateInfo(info: T): Boolean = true
    protected open fun validateInfoBeforeWritingToBuilder(info: T, dependencyId: EntityID<Int>?) = validateInfo(info)
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

/**
 * @see IntIdTableWithPossibleDependency
 * @see IntIdTableWithDependency
 * @see IntIdTableWithNullableDependency
 */
abstract class IntIdTableWithoutDependency<T> : IntIdTableWithPossibleDependency<T, Nothing>(null) {
    final override fun convertInfoToDependencyFormant(info: T): Nothing? = null
}

/**
 * Represents SQL table with nullable [dependency]. (nullable dependency means that column which contains id refers
 * to some row in [dependency] table can possibly be `null`)
 *
 * @see IntIdTableWithPossibleDependency
 * @see IntIdTableWithoutDependency
 * @see IntIdTableWithDependency
 */
abstract class IntIdTableWithNullableDependency<in T, out F>(dependency: IntIdTableWithPossibleDependency<F, *>) :
        IntIdTableWithPossibleDependency<T, F>(dependency) {
    final override fun validateInfoBeforeWritingToBuilder(info: T, dependencyId: EntityID<Int>?): Boolean {
        return super.validateInfoBeforeWritingToBuilder(info, dependencyId)
    }
}

/**
 * Represents SQL table with not nullable dependency (not nullable dependency means that column which contains id refers
 * to some row in [dependency] table cannot be `null`)
 *
 * @see IntIdTableWithPossibleDependency
 * @see IntIdTableWithoutDependency
 * @see IntIdTableWithNullableDependency
 */
abstract class IntIdTableWithDependency<in T, out F>(dependency: IntIdTableWithPossibleDependency<F, *>) :
        IntIdTableWithPossibleDependency<T, F>(dependency) {
    final override fun validateInfoBeforeWritingToBuilder(info: T, dependencyId: EntityID<Int>?): Boolean {
        return dependencyId != null && super.validateInfoBeforeWritingToBuilder(info, dependencyId)
    }

    final override fun writeInfoToBuilder(builder: UpdateBuilder<*>, info: T, dependencyId: EntityID<Int>?) {
        /**
         * can do unsafe dereference as soon as [dependencyId] was checked in [validateInfoBeforeWritingToBuilder]
         */
        writeInfoToBuilderNotNullableDependency(builder, info, dependencyId!!)
    }

    protected abstract fun writeInfoToBuilderNotNullableDependency(builder: UpdateBuilder<*>, info: T, dependencyId: EntityID<Int>)
}
