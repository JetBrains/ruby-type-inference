package org.jetbrains.ruby.codeInsight.types.signature


interface ClassInfo {
    val gemInfo: GemInfo?
    val classFQN: String
    

    data class Impl(override val gemInfo: GemInfo?, override val classFQN: String) : ClassInfo

    fun validate(): Boolean {
        if (classFQN.length > LENGTH_OF_FQN) {
            return false
        }
        val gemInfoVal = gemInfo
        return gemInfoVal == null || gemInfoVal.validate()
    }

    companion object {
        val LENGTH_OF_FQN = 200
    }

}


fun ClassInfo(gemInfo: GemInfo?, classFQN: String) = ClassInfo.Impl(gemInfo, classFQN)

fun ClassInfo(classFQN: String) = ClassInfo.Impl(null, classFQN)

fun ClassInfo(copy: ClassInfo) = with(copy) { ClassInfo.Impl(gemInfo?.let { GemInfo(it) }, classFQN) }

