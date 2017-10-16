package org.jetbrains.ruby.codeInsight.types.signature

object GemInfoConstraint {
    val LENGTH_OF_GEMNAME = 50
    val LENGTH_OF_GEMVERSION = 50
}

interface GemInfo {
    companion object {
        val NONE = Impl("", "")
    }

    val name: String
    val version: String

    data class Impl(override val name: String, override val version: String) : GemInfo

    fun validate(): Boolean {
        return name.length <= GemInfoConstraint.LENGTH_OF_GEMNAME &&
                version.length <= GemInfoConstraint.LENGTH_OF_GEMVERSION
    }
}

fun GemInfo(name: String, version: String) = GemInfo.Impl(name, version)

fun GemInfo(copy: GemInfo) = with(copy) { GemInfo.Impl(name, version) }

fun GemInfoOrNull(name: String, version: String) = GemInfo(name, version).let { if (it == GemInfo.NONE) null else it}
