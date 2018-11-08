package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureProvider

object RSignatureProviderImpl : RSignatureProvider {
    override fun getRegisteredGems(): Collection<GemInfo> {
        return transaction { GemInfoRow.all() }.map { it.copy() }
    }

    override fun getClosestRegisteredGem(usedGem: GemInfo): GemInfo? {
        val (upperBound, lowerBound) = transaction {
            val upperBound = GemInfoTable.select {
                GemInfoTable.name.eq(usedGem.name) and GemInfoTable.version.greaterEq(usedGem.version)
            }
                    .orderBy(GemInfoTable.version)
                    .limit(1)
                    .firstOrNull()
                    ?.let { GemInfoRow.wrapRow(it) }

            val lowerBound = GemInfoTable.select {
                GemInfoTable.name.eq(usedGem.name) and GemInfoTable.version.lessEq(usedGem.version)
            }
                    .orderBy(GemInfoTable.version, isAsc = false)
                    .limit(1)
                    .firstOrNull()
                    ?.let { GemInfoRow.wrapRow(it) }
            return@transaction Pair(upperBound?.copy(), lowerBound?.copy())
        }

        if (lowerBound == null || upperBound == null) {
            return lowerBound ?: upperBound
        } else {
            return if (firstStringCloser(usedGem.version, lowerBound.version, upperBound.version)) lowerBound else upperBound
        }
    }

    override fun getRegisteredClasses(gem: GemInfo): Collection<ClassInfo> {
        return transaction {
            val gemId = GemInfoTable.findRowId(gem) ?: return@transaction listOf()

            return@transaction ClassInfoRow.find { ClassInfoTable.gemInfo eq gemId }.map { it.copy() }
        }
    }

    override fun getAllClassesWithFQN(fqn: String): Collection<ClassInfo> {
        return transaction {
            ClassInfoRow.find { ClassInfoTable.fqn eq fqn }.map { it.copy() }
        }
    }

    override fun getRegisteredMethods(containerClass: ClassInfo): Collection<MethodInfo> {
        return transaction {
            val classId = ClassInfoTable.findRowId(containerClass) ?: return@transaction listOf()

            return@transaction MethodInfoRow.find { MethodInfoTable.classInfo eq classId }.map { it.copy() }
        }
    }

    override fun getSignature(method: MethodInfo): SignatureInfo? {
        return transaction {
            val methodId = MethodInfoTable.findRowId(method) ?: return@transaction null

            return@transaction SignatureContractRow.find { SignatureTable.methodInfo eq methodId }.firstOrNull()?.copy()
        }
    }

    override fun deleteSignature(method: MethodInfo) {
        return transaction {
            val methodId = MethodInfoTable.findRowId(method) ?: return@transaction

            SignatureTable.deleteWhere { SignatureTable.methodInfo eq methodId }
        }
    }

    override fun putSignature(signatureInfo: SignatureInfo) {
        SignatureTable.insertInfoIfNotContains(signatureInfo)
    }

    override fun getRegisteredCallInfos(methodInfo: MethodInfo): List<CallInfo> {
        return transaction {
            val methodId = MethodInfoTable.findRowId(methodInfo) ?: return@transaction listOf()

            return@transaction CallInfoRow.find { CallInfoTable.methodInfoId eq methodId }.map { it.copy() }
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
    return (0 until minLength).firstOrNull { str1[it] != str2[it] } ?: minLength
}
