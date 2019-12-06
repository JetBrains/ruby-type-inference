package org.jetbrains.plugins.ruby

import org.jetbrains.ruby.codeInsight.Injector
import org.jetbrains.ruby.codeInsight.Logger

object RubyDynamicCodeInsightPluginInjector : Injector {
    override fun <T> getLogger(cl: Class<T>): Logger {
        return IdePluginLogger(com.intellij.openapi.diagnostic.Logger.getInstance(cl))
    }
}
