package org.jetbrains.ruby.codeInsight.types.signature

const val ARGUMENTS_TYPES_SEPARATOR = ";"

interface CallInfo {
    val methodInfo: MethodInfo
    val argumentsTypes: List<String>

    fun argumentsTypesJoinToString(): String
}

data class CallInfoImpl(override val methodInfo: MethodInfo, override val argumentsTypes: List<String>) : CallInfo {
    override fun argumentsTypesJoinToString(): String {
        return argumentsTypes.joinToString(separator = ARGUMENTS_TYPES_SEPARATOR)
    }

    constructor(tuple: RTuple) : this(tuple.methodInfo, tuple.argsTypes)
}