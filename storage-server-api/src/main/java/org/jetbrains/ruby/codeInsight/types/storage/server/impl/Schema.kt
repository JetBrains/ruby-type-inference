package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobDeserializer
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobSerializer
import java.sql.Blob
import kotlin.reflect.KProperty

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
    protected abstract fun createSearchCriteriaForInfo(info: T): Op<Boolean>
    protected abstract fun convertInfoToDependencyFormant(info: T): F?

    private data class TraverseResult(var joinedWithDependencies: ColumnSet, var searchCriteria: Op<Boolean>)
    private fun traverseDependencies(info: T): TraverseResult {
        val searchCriteria = createSearchCriteriaForInfo(info)
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

object GemInfoTable : IntIdTableWithoutDependency<GemInfo>() {
    val name = varchar("name", GemInfo.LENGTH_OF_GEMNAME).index()
    val version = varchar("version", GemInfo.LENGTH_OF_GEMVERSION)

    override fun createSearchCriteriaForInfo(info: GemInfo): Op<Boolean> {
        return (name eq info.name) and (version eq info.version)
    }

    override fun writeInfoToBuilder(builder: UpdateBuilder<*>, info: GemInfo, dependencyId: EntityID<Int>?) {
        builder[name] = info.name
        builder[version] = info.version
    }
}

class GemInfoRow(id: EntityID<Int>) : IntEntity(id), GemInfo {
    companion object : IntEntityClass<GemInfoRow>(GemInfoTable)

    override var name: String by GemInfoTable.name
    override var version: String by GemInfoTable.version

    fun copy(): GemInfo = GemInfo(this)
}

object ClassInfoTable : IntIdTableWithPossibleDependency<ClassInfo, GemInfo>(GemInfoTable) {
    val gemInfo = reference("gem_info", GemInfoTable, ReferenceOption.CASCADE).nullable()
    val fqn = varchar("fqn", ClassInfo.LENGTH_OF_FQN)

    override fun createSearchCriteriaForInfo(info: ClassInfo): Op<Boolean> {
        // HACK: as soon as fqn in RubyMine is not fully qualified (search criteria must be: fqn eq info.classFQN)
        return fqn like "%${info.classFQN}"
    }

    override fun convertInfoToDependencyFormant(info: ClassInfo): GemInfo? {
        return info.gemInfo
    }

    override fun writeInfoToBuilder(builder: UpdateBuilder<*>, info: ClassInfo, dependencyId: EntityID<Int>?) {
        builder[fqn] = info.classFQN
        builder[gemInfo] = dependencyId
    }
}

class ClassInfoRow(id: EntityID<Int>) : IntEntity(id), ClassInfo {
    companion object : IntEntityClass<ClassInfoRow>(ClassInfoTable)

    override val gemInfo: GemInfoRow? by GemInfoRow optionalReferencedOn ClassInfoTable.gemInfo
    override val classFQN: String by ClassInfoTable.fqn

    fun copy(): ClassInfo = ClassInfo(this)
}

object MethodInfoTable : IntIdTableWithPossibleDependency<MethodInfo, ClassInfo>(ClassInfoTable) {
    val classInfo = reference("class_info", ClassInfoTable, ReferenceOption.CASCADE)
    val name = varchar("name", MethodInfo.LENGTH_OF_NAME)
    val visibility = enumeration("visibility", RVisibility::class.java)
    val locationFile = varchar("location_file", MethodInfo.LENGTH_OF_PATH).nullable()
    val locationLineno = integer("location_lineno").default(0)

    override fun convertInfoToDependencyFormant(info: MethodInfo): ClassInfo? {
        return info.classInfo
    }

    override fun createSearchCriteriaForInfo(info: MethodInfo): Op<Boolean> {
        return name eq info.name
    }

    override fun writeInfoToBuilder(builder: UpdateBuilder<*>, info: MethodInfo, dependencyId: EntityID<Int>?) {
        builder[classInfo] = dependencyId ?: return
        builder[name] = info.name
        builder[visibility] = info.visibility
        builder[locationFile] = info.location?.path
        builder[locationLineno] = info.location?.lineno ?: 0
    }
}

class MethodInfoRow(id: EntityID<Int>) : IntEntity(id), MethodInfo {
    companion object : IntEntityClass<MethodInfoRow>(MethodInfoTable)

    override var classInfo: ClassInfoRow by ClassInfoRow referencedOn MethodInfoTable.classInfo
    override var name: String by MethodInfoTable.name
    override var visibility: RVisibility by MethodInfoTable.visibility
    override var location: Location? by object {
        operator fun getValue(methodInfoRow: MethodInfoRow, property: KProperty<*>): Location? {
            val file = MethodInfoTable.locationFile.getValue(methodInfoRow, property)
            return file?.let { Location(it, MethodInfoTable.locationLineno.getValue(methodInfoRow, property)) }
        }

        operator fun setValue(methodInfoRow: MethodInfoRow, property: KProperty<*>, location: Location?) {
            MethodInfoTable.locationFile.setValue(methodInfoRow, property, location?.path)
            MethodInfoTable.locationLineno.setValue(methodInfoRow, property, location?.lineno ?: 0)
        }
    }

    override fun toString(): String {
        return name
    }

    fun copy(): MethodInfo = MethodInfo(this)
}

/**
 * Represent SQL table which contains information about every Ruby method call
 */
object CallInfoTable : IntIdTableWithPossibleDependency<CallInfo, MethodInfo>(MethodInfoTable) {
    private const val ARGS_TYPES_STRING_LENGTH = 300
    val methodInfoId = reference("method_info_id", MethodInfoTable, ReferenceOption.NO_ACTION)

    /**
     * string containing types of arguments splitted by semicolon
     */
    var argsTypes = varchar("args_types", ARGS_TYPES_STRING_LENGTH)

    var numberOfArguments = integer("number_of_arguments")

    override fun createSearchCriteriaForInfo(info: CallInfo): Op<Boolean> {
        return argsTypes eq info.argumentsTypesJoinToString()
    }

    override fun convertInfoToDependencyFormant(info: CallInfo): MethodInfo? {
        return info.methodInfo
    }

    override fun writeInfoToBuilder(builder: UpdateBuilder<*>, info: CallInfo, dependencyId: EntityID<Int>?) {
        val argsTypesString = info.argumentsTypesJoinToString()
        if (argsTypesString.length > CallInfoTable.ARGS_TYPES_STRING_LENGTH) {
            return
        }

        builder[methodInfoId] = dependencyId ?: return
        builder[argsTypes] = argsTypesString
        builder[numberOfArguments] = info.argumentsTypes.size
    }

    override fun removeInvalidInfo(validInfo: CallInfo) {
        val methodInfoId = MethodInfoTable.findRowId(validInfo.methodInfo) ?: return
        deleteWhere {
            (CallInfoTable.methodInfoId eq methodInfoId) and (CallInfoTable.numberOfArguments neq validInfo.argumentsTypes.size)
        }
    }
}

class CallInfoRow(id: EntityID<Int>) : IntEntity(id), CallInfo {
    companion object : IntEntityClass<CallInfoRow>(CallInfoTable)

    override val methodInfo: MethodInfoRow by MethodInfoRow referencedOn CallInfoTable.methodInfoId

    private val argsTypesRaw: String by CallInfoTable.argsTypes
    
    override val argumentsTypes: List<String> by lazy {
        // filter { it != "" } is required when argsTypesRaw is empty string
        argsTypesRaw.split(ARGUMENTS_TYPES_SEPARATOR).filter { it != "" }
    }

    override fun argumentsTypesJoinToString(): String {
        return argsTypesRaw
    }

    override fun toString(): String {
        // just for pretty debugging :)
        return argumentsTypes.joinToString(separator = ", ", prefix = "[", postfix = "]")
    }

    fun copy(): CallInfo = CallInfoImpl(methodInfo, argumentsTypes)
}

object SignatureTable : IntIdTableWithPossibleDependency<SignatureInfo, MethodInfo>(MethodInfoTable) {
    val methodInfo = reference("method_info", MethodInfoTable, ReferenceOption.CASCADE)
    val contract = blob("contract")

    override fun insertInfoIfNotContains(info: SignatureInfo): EntityID<Int>? {
        return transaction {
            val methodInfoRow: MethodInfoRow = MethodInfoTable.insertInfoIfNotContains(info.methodInfo)
                    ?.let { MethodInfoRow[it] } ?: return@transaction null

            val existingContractData = SignatureContractRow.find { SignatureTable.methodInfo eq methodInfoRow.id }
                    .firstOrNull()

            if (existingContractData != null) {
                existingContractData.contract = info.contract
            } else {
                SignatureContractRow.new { this.methodInfo = methodInfoRow; contract = info.contract }
            }

            return@transaction SignatureContractRow.new { this.methodInfo = methodInfoRow; contract = info.contract }.id
        }
    }

    override fun writeInfoToBuilder(builder: UpdateBuilder<*>, info: SignatureInfo, dependencyId: EntityID<Int>?) {
        if (dependencyId == null) {
            return
        }

        builder[methodInfo] = dependencyId
        builder[contract] = BlobSerializer.writeToBlob(info.contract, TransactionManager.current().connection.createBlob())
    }

    override fun createSearchCriteriaForInfo(info: SignatureInfo): Op<Boolean> {
        return contract eq BlobSerializer.writeToBlob(info.contract, TransactionManager.current().connection.createBlob())
    }

    override fun convertInfoToDependencyFormant(info: SignatureInfo): MethodInfo {
        return info.methodInfo
    }
}

class SignatureContractRow(id: EntityID<Int>) : IntEntity(id), SignatureInfo {
    companion object : IntEntityClass<SignatureContractRow>(SignatureTable)

    override var methodInfo: MethodInfoRow by MethodInfoRow referencedOn SignatureTable.methodInfo
    override var contract: SignatureContract by BlobDeserializer()

    var contractRaw: Blob by SignatureTable.contract

    fun copy(): SignatureInfo = SignatureInfo(this)
}