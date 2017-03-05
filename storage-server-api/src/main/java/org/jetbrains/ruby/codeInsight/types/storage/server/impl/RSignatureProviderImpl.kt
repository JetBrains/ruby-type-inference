package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.RSignature
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureProvider

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
            Pair(upperBound, lowerBound)
        }

        if (lowerBound == null || upperBound == null) {
            return lowerBound ?: upperBound
        } else {
            return if (firstStringCloser(usedGem.version, lowerBound.version, upperBound.version)) lowerBound else upperBound
        }
    }

    override fun getRegisteredClasses(gem: GemInfo): Collection<ClassInfo> {
        return transaction {
            val gemId = GemInfoTable.slice(GemInfoTable.id)
                    .select { GemInfoTable.name.eq(gem.name) and GemInfoTable.version.eq(gem.version) }
                    .firstOrNull()
                    ?.let {
                        it[GemInfoTable.id]
                    }
                    ?: return@transaction emptyList()

            ClassInfoData.find { ClassInfoTable.gemInfo.eq(gemId) }.toList()
        }
    }

    override fun getRegisteredMethods(containerClass: ClassInfo): Collection<MethodInfo> {
        return transaction {
            val classId = ClassInfoTable.slice(ClassInfoTable.id)
                    .select { ClassInfoTable.fqn.eq(containerClass.classFQN) }
                    .firstOrNull()
                    ?.let {
                        it[ClassInfoTable.id]
                    }
                    ?: return@transaction emptyList()

            MethodInfoData.find { MethodInfoTable.classInfo.eq(classId) }.toList()
        }
    }

    override fun getSignature(gem: GemInfo, method: MethodInfo): RSignature? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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
