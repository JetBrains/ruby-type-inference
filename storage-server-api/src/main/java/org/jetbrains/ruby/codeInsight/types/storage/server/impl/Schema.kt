package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobDeserializer
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobSerializer
import java.sql.Blob
import kotlin.reflect.KProperty

object GemInfoTable : IntIdTableWithoutDependency<GemInfo>() {
    val name = varchar("name", GemInfo.LENGTH_OF_GEMNAME).index()
    val version = varchar("version", GemInfo.LENGTH_OF_GEMVERSION)

    override fun SqlExpressionBuilder.createSearchCriteriaForInfo(info: GemInfo): Op<Boolean> {
        return (name eq info.name) and (version eq info.version)
    }

    override fun validateInfo(info: GemInfo): Boolean {
        return info.name.length <= GemInfo.LENGTH_OF_GEMNAME && info.version.length <= GemInfo.LENGTH_OF_GEMVERSION
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

object ClassInfoTable : IntIdTableWithNullableDependency<ClassInfo, GemInfo>(GemInfoTable) {
    val gemInfo = reference("gem_info", GemInfoTable, ReferenceOption.CASCADE).nullable()
    val fqn = varchar("fqn", ClassInfo.LENGTH_OF_FQN)

    override fun SqlExpressionBuilder.createSearchCriteriaForInfo(info: ClassInfo): Op<Boolean> {
        // HACK: as soon as fqn in RubyMine is not fully qualified (search criteria must be: fqn eq info.classFQN)
        return fqn like "%${info.classFQN}"
    }

    override fun convertInfoToDependencyFormant(info: ClassInfo): GemInfo? {
        return info.gemInfo
    }

    override fun validateInfo(info: ClassInfo): Boolean {
        return info.classFQN.length <= ClassInfo.LENGTH_OF_FQN
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

object MethodInfoTable : IntIdTableWithDependency<MethodInfo, ClassInfo>(ClassInfoTable) {
    val classInfo = reference("class_info", ClassInfoTable, ReferenceOption.CASCADE)
    val name = varchar("name", MethodInfo.LENGTH_OF_NAME)
    val visibility = enumeration("visibility", RVisibility::class)
    val locationFile = varchar("location_file", MethodInfo.LENGTH_OF_PATH).nullable()
    val locationLineno = integer("location_lineno").default(0)

    override fun convertInfoToDependencyFormant(info: MethodInfo): ClassInfo? {
        return info.classInfo
    }

    override fun SqlExpressionBuilder.createSearchCriteriaForInfo(info: MethodInfo): Op<Boolean> {
        return name eq info.name
    }

    override fun validateInfo(info: MethodInfo): Boolean {
        return info.name.length <= MethodInfo.LENGTH_OF_NAME &&
                info.location?.let { it.path.length <= MethodInfo.LENGTH_OF_PATH } ?: true
    }

    override fun writeInfoToBuilderNotNullableDependency(builder: UpdateBuilder<*>, info: MethodInfo, dependencyId: EntityID<Int>) {
        builder[classInfo] = dependencyId
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
object CallInfoTable : IntIdTableWithDependency<CallInfo, MethodInfo>(MethodInfoTable) {
    private const val ARGS_TYPES_STRING_LENGTH = 300
    private const val RETURN_TYPE_STRING_LENGTH = 50
    private const val CALL_INFOS_LIMIT_FOR_PARTICULAR_METHOD = 10

    val methodInfoId = reference("method_info_id", MethodInfoTable, ReferenceOption.NO_ACTION)

    /**
     * string containing types of unnamed args (e.g. REQ, KEYREQ args) splitted by separator
     */
    val unnamedArgsTypes = varchar("required_args_types", ARGS_TYPES_STRING_LENGTH)

    val namedArgsTypes = varchar("named_args_types", ARGS_TYPES_STRING_LENGTH)

    private val numberOfUnnamedArguments = integer("number_of_unnamed_arguments")

    val returnType = varchar("return_type", RETURN_TYPE_STRING_LENGTH)

    /**
     * Deletes all info from [CallInfoTable] related to [methodInfo].
     * **Call this function only inside [transaction] block.**
     */
    fun deleteAllInfoRelatedTo(methodInfo: MethodInfo) {
        val methodInfoId = MethodInfoTable.findRowId(methodInfo) ?: return
        CallInfoTable.deleteWhere {
            CallInfoTable.methodInfoId eq methodInfoId
        }
    }

    override fun insertInfoIfNotContains(info: CallInfo): EntityID<Int>? {
        val count = MethodInfoTable.findRowId(info.methodInfo)?.let {
            CallInfoTable.select {
                CallInfoTable.methodInfoId eq it
            }.count()
        } ?: 0
        if (count >= CALL_INFOS_LIMIT_FOR_PARTICULAR_METHOD) {
            return null
        } else {
            return super.insertInfoIfNotContains(info)
        }
    }

    override fun SqlExpressionBuilder.createSearchCriteriaForInfo(info: CallInfo): Op<Boolean> {
        return (numberOfUnnamedArguments eq info.unnamedArguments.size) and
                (unnamedArgsTypes eq info.unnamedArgumentsTypesJoinToRawString()) and
                (namedArgsTypes eq info.namedArgumentsJoinToRawString()) and
                (returnType eq info.returnType)
    }

    override fun convertInfoToDependencyFormant(info: CallInfo): MethodInfo? {
        return info.methodInfo
    }

    override fun validateInfo(info: CallInfo): Boolean {
        return info.unnamedArgumentsTypesJoinToRawString().length <= ARGS_TYPES_STRING_LENGTH &&
                info.returnType.length <= RETURN_TYPE_STRING_LENGTH
    }

    override fun writeInfoToBuilderNotNullableDependency(builder: UpdateBuilder<*>, info: CallInfo, dependencyId: EntityID<Int>) {
        builder[methodInfoId] = dependencyId
        builder[unnamedArgsTypes] = info.unnamedArgumentsTypesJoinToRawString()
        builder[namedArgsTypes] = info.namedArgumentsJoinToRawString()
        builder[numberOfUnnamedArguments] = info.unnamedArguments.size
        builder[returnType] = info.returnType
    }

    override fun removeInvalidInfo(validInfo: CallInfo) {
        val methodInfoId = MethodInfoTable.findRowId(validInfo.methodInfo) ?: return
        deleteWhere {
            (CallInfoTable.methodInfoId eq methodInfoId) and (CallInfoTable.numberOfUnnamedArguments neq validInfo.unnamedArguments.size)
        }
    }
}

class CallInfoRow(id: EntityID<Int>) : IntEntity(id), CallInfo {
    companion object : IntEntityClass<CallInfoRow>(CallInfoTable)

    private val requiredArgsTypesRaw: String by CallInfoTable.unnamedArgsTypes

    private val namedArgsTypesRaw: String by CallInfoTable.namedArgsTypes

    override val namedArguments: List<ArgumentNameAndType> by lazy {
        namedArgsTypesRaw.takeIf { it != "" }?.split(ARGUMENTS_TYPES_SEPARATOR)?.asSequence()?.map {
            val (name, type) = it.split(ArgumentNameAndType.NAME_AND_TYPE_SEPARATOR)
            return@map ArgumentNameAndType(name, type)
        }?.toList() ?: emptyList()
    }

    override fun namedArgumentsJoinToRawString(): String = namedArgsTypesRaw

    override val methodInfo: MethodInfoRow by MethodInfoRow referencedOn CallInfoTable.methodInfoId

    override val unnamedArguments: List<ArgumentNameAndType> by lazy {
        requiredArgsTypesRaw.takeIf { it != "" }?.split(ARGUMENTS_TYPES_SEPARATOR)?.map {
            val (name, type) = it.split(ArgumentNameAndType.NAME_AND_TYPE_SEPARATOR)
            return@map ArgumentNameAndType(name, type)
        } ?: emptyList()
    }

    override val returnType: String by CallInfoTable.returnType

    override fun unnamedArgumentsTypesJoinToRawString(): String = requiredArgsTypesRaw

    override fun toString(): String {
        // just for pretty debugging :)
        return "name: ${methodInfo.name} unnamedArguments: " + unnamedArguments.joinToString(separator = ", ", prefix = "[", postfix = "]") +
                " return: $returnType"
    }

    fun copy(): CallInfo = CallInfoImpl(methodInfo.copy(), namedArguments, unnamedArguments, returnType)
}

object SignatureTable : IntIdTableWithDependency<SignatureInfo, MethodInfo>(MethodInfoTable) {
    val methodInfo = reference("method_info", MethodInfoTable, ReferenceOption.CASCADE)
    val contract = blob("contract")

    override fun insertInfoIfNotContains(info: SignatureInfo): EntityID<Int>? {
        val methodInfoRow: MethodInfoRow = MethodInfoTable.insertInfoIfNotContains(info.methodInfo)
                ?.let { MethodInfoRow[it] } ?: return null

        val existingContractData = SignatureContractRow.find { SignatureTable.methodInfo eq methodInfoRow.id }
                .firstOrNull()

        if (existingContractData != null) {
            existingContractData.contract = info.contract
        } else {
            SignatureContractRow.new { this.methodInfo = methodInfoRow; contract = info.contract }
        }

        return SignatureContractRow.new { this.methodInfo = methodInfoRow; contract = info.contract }.id
    }

    override fun writeInfoToBuilderNotNullableDependency(builder: UpdateBuilder<*>, info: SignatureInfo, dependencyId: EntityID<Int>) {
        builder[methodInfo] = dependencyId
        builder[contract] = BlobSerializer.writeToBlob(info.contract, TransactionManager.current().connection.createBlob())
    }

    override fun SqlExpressionBuilder.createSearchCriteriaForInfo(info: SignatureInfo): Op<Boolean> {
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