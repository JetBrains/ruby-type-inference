package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException

class RSignatureProviderImpl : RSignatureProvider {
    override fun getClosestRegisteredGem(usedGem: GemInfo): GemInfo? {
        val (upperBound, lowerBound) = transaction {
            val upperBound = GemInfoTable.select {
                GemInfoTable.name.eq(usedGem.name) and GemInfoTable.version.greaterEq(usedGem.version)
            }
                    .orderBy(GemInfoTable.version)
                    .limit(1)
                    .firstOrNull()
                    ?.let { GemInfoData.wrapRow(it, TransactionManager.current()) }

            val lowerBound = GemInfoTable.select {
                GemInfoTable.name.eq(usedGem.name) and GemInfoTable.version.lessEq(usedGem.version)
            }
                    .orderBy(GemInfoTable.version, isAsc = false)
                    .limit(1)
                    .firstOrNull()
                    ?.let { GemInfoData.wrapRow(it, TransactionManager.current()) }
            Pair(upperBound?.copy(), lowerBound?.copy())
        }

        if (lowerBound == null || upperBound == null) {
            return lowerBound ?: upperBound
        } else {
            return if (firstStringCloser(usedGem.version, lowerBound.version, upperBound.version)) lowerBound else upperBound
        }
    }

    override fun getRegisteredClasses(gem: GemInfo): Collection<ClassInfo> {
        return transaction {
            val gemId: EntityID<Int>
            if (gem is GemInfoData) {
                gemId = gem.id
            } else {
                gemId = GemInfoTable.slice(GemInfoTable.id)
                        .select { GemInfoTable.name.eq(gem.name) and GemInfoTable.version.eq(gem.version) }
                        .firstOrNull()
                        ?.let {
                            it[GemInfoTable.id]
                        }
                        ?: return@transaction emptyList()
            }

            ClassInfoData.find { ClassInfoTable.gemInfo.eq(gemId) }.toList().map { it.copy() }
        }
    }

    override fun getRegisteredMethods(containerClass: ClassInfo): Collection<MethodInfo> {
        return transaction {
            val classId: EntityID<Int>
            if (containerClass is ClassInfoData) {
                classId = containerClass.id
            } else {
                val selectGemClause = getGemWhereClause(containerClass)
                classId = (ClassInfoTable leftJoin GemInfoTable).slice(ClassInfoTable.id)
                        .select { ClassInfoTable.fqn.eq(containerClass.classFQN) and selectGemClause() }
                        .firstOrNull()
                        ?.let {
                            it[ClassInfoTable.id]
                        }
                        ?: return@transaction emptyList()
            }

            MethodInfoData.find { MethodInfoTable.classInfo.eq(classId) }.toList().map { it.copy() }
        }
    }

    override fun getSignature(method: MethodInfo): SignatureInfo? {
        return transaction {
            val methodId = findMethodId(method)
                    ?: return@transaction null

            SignatureContractData.find { SignatureTable.methodInfo.eq(methodId) }.firstOrNull()?.copy()
        }
    }

    override fun deleteSignature(method: MethodInfo) {
        return transaction {
            val methodId = findMethodId(method)
                    ?: return@transaction

            SignatureTable.deleteWhere { SignatureTable.methodInfo eq methodId }
        }
    }

    override fun putSignature(signatureInfo: SignatureInfo) {
        return transaction {
            val lazyGemInfoId = lazy {
                signatureInfo.methodInfo.classInfo.gemInfo?.let { givenGemInfo ->
                    (givenGemInfo as? GemInfoData)?.id
                            ?: with(GemInfoTable) {
                        select {
                            (name eq givenGemInfo.name) and (version eq givenGemInfo.version)
                        }.firstOrNull()?.get(id)
                    }
                            ?: GemInfoTable.insertAndGetId {
                        it[name] = givenGemInfo.name
                        it[version] = givenGemInfo.version
                    }
                            ?: throw StorageException("Could not retrieve nor insert gem info: $givenGemInfo")
                }
            }

            val lazyClassInfoId = lazy {
                signatureInfo.methodInfo.classInfo.let { givenClassInfo ->
                    (givenClassInfo as? ClassInfoData)?.id
                            ?: with(ClassInfoTable) {
                        select {
                            (fqn eq givenClassInfo.classFQN) and (gemInfo eq lazyGemInfoId.value)
                        }
                                .firstOrNull()?.get(id)
                    }
                            ?: ClassInfoTable.insertAndGetId {
                        it[fqn] = givenClassInfo.classFQN
                        it[gemInfo] = lazyGemInfoId.value
                    }
                            ?: throw StorageException("Could not retrieve nor insert class info: $givenClassInfo")
                }
            }

            val methodInfoData = signatureInfo.methodInfo.let { givenMethodInfo ->
                givenMethodInfo as? MethodInfoData
                        ?: with(MethodInfoTable) {
                    MethodInfoData.find {
                        (name eq givenMethodInfo.name) and
                                (visibility eq givenMethodInfo.visibility) and
                                (locationFile eq givenMethodInfo.location?.path) and
                                (locationLineno eq (givenMethodInfo.location?.lineno ?: 0)) and
                                (classInfo eq lazyClassInfoId.value)
                    }.firstOrNull()
                }
                        ?: MethodInfoTable.insertAndGetId {
                    it[name] = givenMethodInfo.name
                    it[visibility] = givenMethodInfo.visibility
                    it[locationFile] = givenMethodInfo.location?.path
                    it[locationLineno] = givenMethodInfo.location?.lineno ?: 0
                    it[classInfo] = lazyClassInfoId.value
                }?.let {
                    MethodInfoData[it]
                }
                        ?: throw StorageException("Could not retrieve not insert method info: $givenMethodInfo")
            }

            val existingContractData = SignatureContractData.find { SignatureTable.methodInfo eq methodInfoData.id }
                    .firstOrNull()

            if (existingContractData != null) {
                existingContractData.contract = signatureInfo.contract
            } else {
                SignatureContractData.new { this.methodInfo = methodInfoData; contract = signatureInfo.contract }
            }
        }
    }

    private fun findMethodId(method: MethodInfo): EntityID<Int>? {
        if (method is MethodInfoData) {
            return method.id
        }

        val selectGemClause = getGemWhereClause(method.classInfo)
        return (MethodInfoTable innerJoin ClassInfoTable leftJoin GemInfoTable).slice(MethodInfoTable.id)
                .select {
                    MethodInfoTable.name.eq(method.name) and
                            ClassInfoTable.fqn.eq(method.classInfo.classFQN) and
                            selectGemClause()
                }.firstOrNull()
                ?.let {
                    it[MethodInfoTable.id]
                }
    }

    private fun getGemWhereClause(containerClass: ClassInfo): SqlExpressionBuilder.() -> Op<Boolean> {
        val gemInfo = containerClass.gemInfo
        if (gemInfo == null) {
            return { ClassInfoTable.gemInfo.isNull() }
        } else {
            return {
                GemInfoTable.name.eq(gemInfo.name) and
                        GemInfoTable.version.eq(gemInfo.version)
            }
        }
    }

}

fun firstStringCloser(gemVersion: String,
                      firstVersion: String, secondVersion: String): Boolean {
    val lcpLengthFirst = longestCommonPrefixLength(gemVersion, firstVersion)
    val lcpLengthSecond = longestCommonPrefixLength(gemVersion, secondVersion)
    return lcpLengthFirst > lcpLengthSecond || lcpLengthFirst > 0 && lcpLengthFirst == lcpLengthSecond &&
            Math.abs(gemVersion.rawChar(lcpLengthFirst) - firstVersion.rawChar(lcpLengthFirst)) <
                    Math.abs(gemVersion.rawChar(lcpLengthFirst) - secondVersion.rawChar(lcpLengthSecond))
}

private fun String.rawChar(index: Int): Int = if (index < length) this[index].toInt() else 0

private fun longestCommonPrefixLength(str1: String, str2: String): Int {
    val minLength = Math.min(str1.length, str2.length)
    return (0..minLength - 1).firstOrNull { str1[it] != str2[it] }
            ?: minLength
}
