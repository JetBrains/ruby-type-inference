package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobDeserializer
import java.sql.Blob
import kotlin.reflect.KProperty

object GemInfoTable : IntIdTable() {
    val name = varchar("name", 50).index()
    val version = varchar("version", 50)
}

class GemInfoData(id: EntityID<Int>) : IntEntity(id), GemInfo {
    companion object : IntEntityClass<GemInfoData>(GemInfoTable)

    override var name: String by GemInfoTable.name
    override var version: String by GemInfoTable.version

    fun copy() = GemInfo(this)
}

object ClassInfoTable : IntIdTable() {
    val gemInfo = reference("gem_info", GemInfoTable).nullable()
    val fqn = varchar("fqn", 200)
}

class ClassInfoData(id: EntityID<Int>) : IntEntity(id), ClassInfo {
    companion object : IntEntityClass<ClassInfoData>(ClassInfoTable)

    override var gemInfo: GemInfoData? by GemInfoData optionalReferencedOn ClassInfoTable.gemInfo
    override var classFQN: String by ClassInfoTable.fqn

    fun copy() = ClassInfo(this)
}

object MethodInfoTable : IntIdTable() {
    val classInfo = reference("class_info", ClassInfoTable)
    val name = varchar("name", 100)
    val visibility = enumeration("visibility", RVisibility::class.java)
    val locationFile = varchar("location_file", 1000).nullable()
    val locationLineno = integer("location_lineno").default(0)
}

class MethodInfoData(id: EntityID<Int>) : IntEntity(id), MethodInfo {
    companion object : IntEntityClass<MethodInfoData>(MethodInfoTable)

    override var classInfo: ClassInfoData by ClassInfoData referencedOn MethodInfoTable.classInfo
    override var name: String by MethodInfoTable.name
    override var visibility: RVisibility by MethodInfoTable.visibility
    override var location: Location? by object {
        operator fun getValue(methodInfoData: MethodInfoData, property: KProperty<*>): Location? {
            val file = MethodInfoTable.locationFile.getValue(methodInfoData, property)
            return file?.let { Location(it, MethodInfoTable.locationLineno.getValue(methodInfoData, property)) }
        }

        operator fun setValue(methodInfoData: MethodInfoData, property: KProperty<*>, location: Location?) {
            MethodInfoTable.locationFile.setValue(methodInfoData, property, location?.path)
            MethodInfoTable.locationLineno.setValue(methodInfoData, property, location?.lineno ?: 0)
        }
    }

    fun copy() = MethodInfo(this)
}

object SignatureTable : IntIdTable() {
    val methodInfo = reference("method_info", MethodInfoTable)
    val contract = blob("contract")
}

class SignatureContractData(id: EntityID<Int>) : IntEntity(id), SignatureInfo {
    companion object : IntEntityClass<SignatureContractData>(SignatureTable)

    override var methodInfo: MethodInfoData by MethodInfoData referencedOn SignatureTable.methodInfo
    override var contract: SignatureContract by BlobDeserializer()

    var contractRaw: Blob by SignatureTable.contract

    fun copy() = SignatureInfo(this)
}