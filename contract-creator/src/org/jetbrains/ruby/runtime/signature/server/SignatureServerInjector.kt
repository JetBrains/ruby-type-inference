package org.jetbrains.ruby.runtime.signature.server

import org.jetbrains.ruby.codeInsight.Injector
import org.jetbrains.ruby.codeInsight.Logger
import org.jetbrains.ruby.codeInsight.PrintToStdoutLogger

object SignatureServerInjector : Injector {
    override fun <T> getLogger(cl: Class<T>): Logger {
        return PrintToStdoutLogger(cl)
    }
}
