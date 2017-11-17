package org.jetbrains.ruby.runtime.signature.server.serialisation

import org.jetbrains.ruby.codeInsight.types.signature.*

interface RCachedMethod {
    val methodInfo: MethodInfo
    val id: Int
    val argsInfoString: String

    data class Impl(override val methodInfo: MethodInfo,
                    override val id: Int,
                    override val argsInfoString: String) : RCachedMethod
}

fun RCachedMethod(methodInfo: MethodInfo, id: Int, argsInfoString: String) = RCachedMethod.Impl(methodInfo, id, argsInfoString)