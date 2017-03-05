package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.RVisibility

object GemInfoTable : IntIdTable() {
    val name = varchar("name", 50).index()
    val version = varchar("version", 50)
}

class GemInfoData(id: EntityID<Int>) : IntEntity(id), GemInfo {
    companion object : IntEntityClass<GemInfoData>(GemInfoTable)

    override val name: String by GemInfoTable.name
    override val version: String by GemInfoTable.version
}

object ClassInfoTable : IntIdTable() {
    val gemInfo = reference("gem_info", GemInfoTable).nullable()
    val fqn = varchar("fqn", 100)
}

class ClassInfoData(id: EntityID<Int>) : IntEntity(id), ClassInfo {
    companion object : IntEntityClass<ClassInfoData>(ClassInfoTable)

    override val gemInfo: GemInfo? by GemInfoData optionalReferencedOn ClassInfoTable.gemInfo
    override val classFQN: String by ClassInfoTable.fqn
}

object MethodInfoTable : IntIdTable() {
    val classInfo = reference("class_info", ClassInfoTable)
    val name = varchar("name", 50)
    val visibility = enumeration("visibility", RVisibility::class.java)
}

class MethodInfoData(id: EntityID<Int>) : IntEntity(id), MethodInfo {
    companion object : IntEntityClass<MethodInfoData>(MethodInfoTable)

    override val classInfo: ClassInfo by ClassInfoData referencedOn MethodInfoTable.classInfo
    override val name: String by MethodInfoTable.name
    override val visibility: RVisibility by MethodInfoTable.visibility

}

object SignatureTable : IntIdTable() {
    val gemInfo = reference("gem_info", GemInfoTable.id)
    val methodInfo = reference("method_info", MethodInfoTable.id)
    val returnType = varchar("return_type", 100)
}