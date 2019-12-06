package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ruby.RubyDynamicCodeInsightPluginInjector
import org.jetbrains.ruby.codeInsight.initInjector

class RubyDynamicCodeInsightPluginAppLifecyctlListener : AppLifecycleListener {
    override fun appStarting(projectFromCommandLine: Project?) {
        initInjector(RubyDynamicCodeInsightPluginInjector)
    }
}
