package org.jetbrains.ruby.codeInsight.types.signature

const val ARGUMENTS_TYPES_SEPARATOR = ";"

interface CallInfo {
    val methodInfo: MethodInfo

    /**
     * Types of unnamed arguments (e.g. REQ, OPT, REST, POST)
     */
    val unnamedArguments: List<ArgumentNameAndType>

    /**
     * Types of named arguments sorted alphabetically by [ArgumentNameAndType.name]
     */
    val namedArguments: List<ArgumentNameAndType>

    val returnType: String

    /**
     * Join [unnamedArguments] to raw [String] which is used in database
     */
    fun unnamedArgumentsTypesJoinToRawString(): String

    /**
     * Join [namedArgumentsJoinToRawString] to raw [String] which is used in database.
     * Should return concatenated string containing elements ordered by argument name alphabetically
     */
    fun namedArgumentsJoinToRawString(): String

    fun getTypeNameByArgumentName(name: String): String? {
        return (unnamedArguments.find { it.name == name } ?: namedArguments.find { it.name == name })?.type
    }
}

data class ArgumentNameAndType(val name: String, val type: String) {
    companion object {
        const val NAME_AND_TYPE_SEPARATOR = ","
        /**
         * For such method:
         * def foo(a, b = 1); end
         *
         * And such call:
         * foo(a)
         * `b` is implicitly passed
         */
        const val IMPLICITLY_PASSED_ARGUMENT_TYPE = "-"
    }
}

class CallInfoImpl(override val methodInfo: MethodInfo,
                   namedArguments: List<ArgumentNameAndType>,
                   override val unnamedArguments: List<ArgumentNameAndType>,
                   override val returnType: String) : CallInfo {
    override val namedArguments = namedArguments.sortedBy { it.name }

    override fun namedArgumentsJoinToRawString(): String =
            namedArguments.joinToString(separator = ARGUMENTS_TYPES_SEPARATOR) { it.name + "," + it.type }

    override fun unnamedArgumentsTypesJoinToRawString(): String =
            unnamedArguments.joinToString(separator = ARGUMENTS_TYPES_SEPARATOR) { it.name + "," + it.type }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallInfo) return false

        other as CallInfoImpl

        return methodInfo == other.methodInfo &&
               unnamedArguments == other.unnamedArguments &&
               returnType == other.returnType &&
               namedArguments == other.namedArguments
    }

    override fun hashCode(): Int {
        var result = methodInfo.hashCode()
        result = 31 * result + unnamedArguments.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + namedArguments.hashCode()
        return result
    }

    override fun toString(): String {
        return "CallInfoIml(methodInfo=$methodInfo, namedArguments=$namedArguments, unnamedArguments=$unnamedArguments, returnType=$returnType)"
    }
}
