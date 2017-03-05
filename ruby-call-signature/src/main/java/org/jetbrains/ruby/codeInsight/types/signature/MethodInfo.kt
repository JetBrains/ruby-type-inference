package org.jetbrains.ruby.codeInsight.types.signature

interface MethodInfo {
    val classInfo: ClassInfo
    val name: String
    val visibility: RVisibility

    data class Impl(override val classInfo: ClassInfo,
                    override val name: String,
                    override val visibility: RVisibility) : MethodInfo
}

fun MethodInfo(classInfo: ClassInfo, name: String, visibility: RVisibility) = MethodInfo.Impl(classInfo, name, visibility)

enum class RVisibility constructor(val value: Byte, val presentableName: String) {
    PRIVATE(0, "PRIVATE"),
    PROTECTED(1, "PROTECTED"),
    PUBLIC(2, "PUBLIC");
}
