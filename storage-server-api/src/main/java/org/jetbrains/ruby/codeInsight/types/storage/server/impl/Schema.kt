package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobDeserializer
import java.sql.Blob
import kotlin.reflect.KProperty

object GemInfoTable : IntIdTable() {
    val name = varchar("name", GemInfo.LENGTH_OF_GEMNAME).index()
    val version = varchar("version", GemInfo.LENGTH_OF_GEMVERSION)
}

class GemInfoRow(id: EntityID<Int>) : IntEntity(id), GemInfo {
    companion object : IntEntityClass<GemInfoRow>(GemInfoTable)

    override var name: String by GemInfoTable.name
    override var version: String by GemInfoTable.version

    fun copy(): GemInfo = GemInfo(this)
}

object ClassInfoTable : IntIdTable() {
    val gemInfo = reference("gem_info", GemInfoTable, ReferenceOption.CASCADE).nullable()
    val fqn = varchar("fqn", ClassInfo.LENGTH_OF_FQN)
}


class ClassInfoRow(id: EntityID<Int>) : IntEntity(id), ClassInfo {
    companion object : IntEntityClass<ClassInfoRow>(ClassInfoTable)

    override val gemInfo: GemInfoRow? by GemInfoRow optionalReferencedOn ClassInfoTable.gemInfo
    override val classFQN: String by ClassInfoTable.fqn

    fun copy(): ClassInfo = ClassInfo(this)
}

object MethodInfoTable : IntIdTable() {
    val classInfo = reference("class_info", ClassInfoTable, ReferenceOption.CASCADE)
    val name = varchar("name", MethodInfo.LENGTH_OF_NAME)
    val visibility = enumeration("visibility", RVisibility::class.java)
    val locationFile = varchar("location_file", MethodInfo.LENGTH_OF_PATH).nullable()
    val locationLineno = integer("location_lineno").default(0)
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

    fun copy(): MethodInfo = MethodInfo(this)
}

object SignatureTable : IntIdTable() {
    val methodInfo = reference("method_info", MethodInfoTable, ReferenceOption.CASCADE)
    val contract = blob("contract")
}

class SignatureContractRow(id: EntityID<Int>) : IntEntity(id), SignatureInfo {
    companion object : IntEntityClass<SignatureContractRow>(SignatureTable)

    override var methodInfo: MethodInfoRow by MethodInfoRow referencedOn SignatureTable.methodInfo
    override var contract: SignatureContract by BlobDeserializer()

    var contractRaw: Blob by SignatureTable.contract

    fun copy(): SignatureInfo = SignatureInfo(this)
}