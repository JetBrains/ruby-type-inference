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
}
