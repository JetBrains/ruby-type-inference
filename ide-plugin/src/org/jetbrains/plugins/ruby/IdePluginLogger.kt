package org.jetbrains.plugins.ruby

import org.jetbrains.ruby.codeInsight.Logger

class IdePluginLogger(private val intellijPlatformLogger: com.intellij.openapi.diagnostic.Logger) : Logger {
    override fun info(msg: String) {
        intellijPlatformLogger.info(msg)
    }
}
