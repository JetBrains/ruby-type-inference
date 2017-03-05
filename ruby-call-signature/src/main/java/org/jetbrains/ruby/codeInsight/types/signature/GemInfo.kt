package org.jetbrains.ruby.codeInsight.types.signature

interface GemInfo {
    companion object {
        val NONE = Impl("", "")
    }

    val name: String
    val version: String

    data class Impl(override val name: String, override val version: String) : GemInfo
}

fun GemInfo(name: String, version: String) = GemInfo.Impl(name, version)
