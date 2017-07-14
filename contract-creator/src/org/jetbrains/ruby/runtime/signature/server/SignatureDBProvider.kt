package org.jetbrains.ruby.runtime.signature.server

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.*

class SignatureDBProvider {

    init {
        connectToDB()
    }

    fun connectToDB() {

        Database.connect("jdbc:mysql://localhost:3306/rsignatures", driver = "com.mysql.jdbc.Driver", user = "root", password = "12345")

        transaction { SchemaUtils.create(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable) }
    }

    fun getSignatureContract(methodInfo: MethodInfo): SignatureContract? {

        return transaction {
            val gemInfo = methodInfo.classInfo.gemInfo
            val classInfo = methodInfo.classInfo
            val gemInfoId: EntityID<Int>?

            if (gemInfo != null) {
                gemInfoId = GemInfoTable.select { GemInfoTable.name.eq(gemInfo.name) and GemInfoTable.version.eq(gemInfo.version) }.firstOrNull()?.get(GemInfoTable.id)
            } else {
                gemInfoId = null
            }

            val insertedClassInfo = ClassInfoTable.select { ClassInfoTable.gemInfo.eq(gemInfoId) and ClassInfoTable.fqn.eq(classInfo.classFQN) }.firstOrNull()?.get(ClassInfoTable.id) ?: return@transaction null

            val insertedMethodInfo = MethodInfoTable.select {
                MethodInfoTable.classInfo.eq(insertedClassInfo) and MethodInfoTable.name.eq(methodInfo.name) and
                        MethodInfoTable.visibility.eq(methodInfo.visibility) and MethodInfoTable.locationFile.eq(methodInfo.location?.path) and MethodInfoTable.locationLineno.eq(methodInfo.location?.lineno ?: 0)
            }.firstOrNull()?.get(MethodInfoTable.id) ?: return@transaction null

            return@transaction SignatureContractData.find { SignatureTable.methodInfo.eq(insertedMethodInfo) }.firstOrNull()?.contract
        }
    }

    fun putSignatureContract(methodInfo: MethodInfo, contract: SignatureContract) {

        transaction {

            val gemInfo = methodInfo.classInfo.gemInfo
            val insertedGemInfo: EntityID<Int>?


            insertedGemInfo = GemInfoTable
                    .select { GemInfoTable.name.eq(gemInfo?.name ?: "") and GemInfoTable.version.eq(gemInfo?.version ?: "") }
                    .firstOrNull()
                    ?.get(GemInfoTable.id)
                    ?: GemInfoTable.insertAndGetId { it[name] = gemInfo?.name; it[version] = gemInfo?.version }

            val classInfo = methodInfo.classInfo
            val insertedClassInfo = ClassInfoTable.select { ClassInfoTable.gemInfo.eq(insertedGemInfo) and ClassInfoTable.fqn.eq(classInfo.classFQN) }.firstOrNull()?.get(ClassInfoTable.id)
                    ?: ClassInfoTable.insertAndGetId { it[ClassInfoTable.gemInfo] = insertedGemInfo; it[fqn] = classInfo.classFQN }
                    ?: throw AssertionError("sjdhfkjsd")


            val insertedMethodInfo = MethodInfoTable.select {
                MethodInfoTable.classInfo.eq(insertedClassInfo) and MethodInfoTable.name.eq(methodInfo.name) and
                        MethodInfoTable.visibility.eq(methodInfo.visibility) and MethodInfoTable.locationFile.eq(methodInfo.location?.path) and MethodInfoTable.locationLineno.eq(methodInfo.location?.lineno ?: 0)
            }.firstOrNull()?.get(MethodInfoTable.id)
                    ?: MethodInfoTable.insertAndGetId { it[MethodInfoTable.classInfo] = insertedClassInfo; it[name] = methodInfo.name; it[visibility] = methodInfo.visibility; it[locationFile] = methodInfo.location?.path; it[locationLineno] = methodInfo.location?.lineno }
                    ?: throw AssertionError("sjdhfkjsd")

            val insertedSignature = SignatureTable.select { SignatureTable.methodInfo.eq(insertedMethodInfo) }.firstOrNull()?.get(SignatureTable.id)

            val blob = TransactionManager.current().connection.createBlob()
            if (insertedSignature == null) {
                SignatureTable.insertIgnore { it[SignatureTable.contract] = BlobSerializer.writeToBlob(contract, blob); it[SignatureTable.methodInfo] = insertedMethodInfo }
            } else {
                SignatureTable.update({ SignatureTable.id.eq(insertedSignature) }) {
                    it[SignatureTable.contract] = BlobSerializer.writeToBlob(contract, blob)
                }
            }
        }
    }

    fun getRegisteredContractsWithInfos(): Collection<SignatureContractData> {
        return transaction {
            return@transaction SignatureContractData.all().toList()
        }
    }
}