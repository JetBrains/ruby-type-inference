package org.jetbrains.ruby.codeInsight.types.signature

import java.util.NoSuchElementException

interface GemInfo {
    val name: String
    val version: String

    data class Impl(override val name: String, override val version: String) : GemInfo

    fun validate(): Boolean {
        return name.length <= LENGTH_OF_GEMNAME &&
                version.length <= LENGTH_OF_GEMVERSION
    }

    companion object {
        val NONE = Impl("", "")
        val LENGTH_OF_GEMNAME = 50
        val LENGTH_OF_GEMVERSION = 50
    }
}

fun GemInfo(name: String, version: String) = GemInfo.Impl(name, version)

fun GemInfo(copy: GemInfo) = with(copy) { GemInfo.Impl(name, version) }

fun GemInfoOrNull(name: String, version: String) = GemInfo(name, version).let { if (it == GemInfo.NONE) null else it}

fun GemInfoFromFilePathOrNull(path: String): GemInfo? {
    val gemPathPattern = """([A-Za-z0-9_-]+)-(\d+[0-9A-Za-z.]+)"""
    val regex = Regex(gemPathPattern)
    return try {
        val (gemName, gemVersion) = regex.findAll(path).last().destructured
        GemInfo(gemName, gemVersion)
    } catch (ex: NoSuchElementException) { // if the sequence returned by regex.findAll is empty
        null
    }
}