package org.jetbrains.ruby.codeInsight.types.signature

interface SignatureInfo {
    val methodInfo: MethodInfo
    val contract: SignatureContract

    data class Impl(override val methodInfo: MethodInfo, override val contract: SignatureContract) : SignatureInfo
}

fun SignatureInfo(methodInfo: MethodInfo, contract: SignatureContract) = SignatureInfo.Impl(methodInfo, contract)

fun SignatureInfo(copy: SignatureInfo) = with(copy) { SignatureInfo.Impl(MethodInfo(methodInfo), contract) }