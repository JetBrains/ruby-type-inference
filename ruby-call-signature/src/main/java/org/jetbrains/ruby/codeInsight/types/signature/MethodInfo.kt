package org.jetbrains.ruby.codeInsight.types.signature

interface MethodInfo {
    val classInfo: ClassInfo
    val name: String
    val visibility: RVisibility
    val location: Location?

    data class Impl(override val classInfo: ClassInfo,
                    override val name: String,
                    override val visibility: RVisibility = RVisibility.PUBLIC,
                    override val location: Location? = null) : MethodInfo

    fun validate(): Boolean {
        if (name.length > LENGTH_OF_NAME) {
            return false
        }
        val loc = location
        if (loc == null || loc.path.length > LENGTH_OF_PATH) {
            return false
        }
        return classInfo.validate()
    }

    companion object {
        val LENGTH_OF_NAME = 100
        val LENGTH_OF_PATH = 1000
    }
}

@JvmOverloads
fun MethodInfo(classInfo: ClassInfo, name: String, visibility: RVisibility, location: Location? = null) =
        MethodInfo.Impl(classInfo, name, visibility, location)

fun MethodInfo(copy: MethodInfo) = with(copy) { MethodInfo.Impl(ClassInfo(classInfo), name, visibility, location) }

data class Location(val path: String, val lineno: Int)

enum class RVisibility constructor(val value: Byte, val presentableName: String) {
    PRIVATE(0, "PRIVATE"),
    PROTECTED(1, "PROTECTED"),
    PUBLIC(2, "PUBLIC");
}