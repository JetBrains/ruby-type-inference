package org.jetbrains.ruby.codeInsight.types.signature

interface ClassInfo {
    val gemInfo: GemInfo?
    val classFQN: String

    data class Impl(override val gemInfo: GemInfo?, override val classFQN: String) : ClassInfo
}


fun ClassInfo(gemInfo: GemInfo, classFQN: String) = ClassInfo.Impl(gemInfo, classFQN)

fun ClassInfo(classFQN: String) = ClassInfo.Impl(null, classFQN)

