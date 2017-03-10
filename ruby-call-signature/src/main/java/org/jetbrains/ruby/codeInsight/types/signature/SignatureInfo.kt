package org.jetbrains.ruby.codeInsight.types.signature

interface SignatureInfo {
    val methodInfo: MethodInfo
    val contract: SignatureContract

    data class Impl(override val methodInfo: MethodInfo, override val contract: SignatureContract) : SignatureInfo
}