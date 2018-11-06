package org.jetbrains.ruby.runtime.signature.server.serialisation

import org.jetbrains.ruby.codeInsight.types.signature.*

data class ServerResponseBean(
        val method_name: String,
        /**
         * Number of unnamedArguments passed by user explicitly
         *
         * For example for method:
         * def foo(a, b = 1); end
         *
         * This method invocation have only one explicit argument
         * foo(4)
         *
         * But this method invocation have two explicit unnamedArguments
         * foo(4, 5)
         */
        val call_info_argc: Int,
        val args_info: String,
        val visibility: String,
        val path: String,
        val lineno: Int,
        val receiver_name: String,
        val return_type_name: String)

// explicit here means that this unnamedArguments was explicitly provided by user
// for example:
// def foo(a, b = 1); end
// foo(1)    # here only `a` is explicitly provided
// foo(1, 5) # here `a` and `b` are both explicitly provided
private data class Arg(val paramInfo: ParameterInfo, val type: String, var explicit: Boolean)

private const val PARAMETER_MODIFIER_INDEX_IN_ATTRIBUTES = 0
private const val PARAMETER_TYPE_INDEX_IN_ATTRIBUTES = 1
private const val PARAMETER_NAME_INDEX_IN_ATTRIBUTES = 2
private const val NUMBER_OF_ATTRIBUTES_FOR_PARAMETER = 3

fun ServerResponseBean.toCallInfo(): CallInfo {
    var argc = this.call_info_argc

    val args = this.args_info.takeIf { it != "" }?.split(";")?.map {
        val parts: List<String> = it.split(",")
        val modifier = parts[PARAMETER_MODIFIER_INDEX_IN_ATTRIBUTES]
        val type = parts[PARAMETER_TYPE_INDEX_IN_ATTRIBUTES]

        val name = if (parts.size == NUMBER_OF_ATTRIBUTES_FOR_PARAMETER) {
            // It's possible that parameter in ruby doesn't have name, for example:
            // def foo(*); end
            parts[PARAMETER_NAME_INDEX_IN_ATTRIBUTES]
        } else {
            ""
        }

        // If argc == -1 then all args are explicitly passed
        return@map Arg(ParameterInfo(name, ParameterInfo.Type.valueOf(modifier)), type, explicit = argc == -1)
    } ?: emptyList()

    if (argc != -1) {
        for (arg in args) {
            if (arg.paramInfo.isNamedParameter ||
                    arg.paramInfo.modifier == ParameterInfo.Type.REQ ||
                    arg.paramInfo.modifier == ParameterInfo.Type.POST) {
                arg.explicit = true
                argc--
            }
        }

        for (arg in args) {
            if (argc <= 0) {
                break
            }
            if (arg.paramInfo.modifier == ParameterInfo.Type.OPT) {
                arg.explicit = true
                argc--
            }
        }

        for (arg in args) {
            if (argc <= 0) {
                break
            }
            if (arg.paramInfo.modifier == ParameterInfo.Type.REST) {
                arg.explicit = true
                argc--
            }
        }

        assert(argc == 0 || args.any { it.paramInfo.modifier == ParameterInfo.Type.BLOCK } && argc == 1)
    }

    val namedArgumentsNamesToTypes = args.asSequence().filter { it.paramInfo.isNamedParameter }
            .map { ArgumentNameAndType(it.paramInfo.name, it.type) }.toList()

    val unnamedArgumentsTypes = args.asSequence().filter { !it.paramInfo.isNamedParameter }
            .map { arg ->
                ArgumentNameAndType(arg.paramInfo.name, arg.type.takeIf { arg.explicit }
                        ?: ArgumentNameAndType.IMPLICITLY_PASSED_ARGUMENT_TYPE)
            }.toList()

    val methodInfo = MethodInfo.Impl(
            ClassInfo.Impl(gemInfoFromFilePathOrNull(this.path), this.receiver_name),
            this.method_name,
            RVisibility.valueOf(this.visibility),
            Location(this.path, this.lineno))

    return CallInfoImpl(methodInfo, namedArgumentsNamesToTypes, unnamedArgumentsTypes, this.return_type_name)
}
