package org.jetbrains.ruby.codeInsight.types.signature

const val ARGUMENTS_TYPES_SEPARATOR = ";"

interface CallInfo {
    val methodInfo: MethodInfo
    val argumentsTypes: List<String>
    val returnType: String

    fun argumentsTypesJoinToString(): String

    val numberOfArguments: Int
        get() = argumentsTypes.size
}

data class CallInfoImpl(override val methodInfo: MethodInfo, override val argumentsTypes: List<String>,
                        override val returnType: String) : CallInfo {
    override fun argumentsTypesJoinToString(): String {
        return argumentsTypes.joinToString(separator = ARGUMENTS_TYPES_SEPARATOR)
    }

    constructor(tuple: RTuple) : this(tuple.methodInfo, tuple.argsTypes, tuple.returnTypeName)
}