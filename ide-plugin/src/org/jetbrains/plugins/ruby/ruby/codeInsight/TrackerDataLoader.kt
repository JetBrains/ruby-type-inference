package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.RubyClassHierarchyWithCaching

class TrackerDataLoader : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {

        ModuleManager.getInstance(project).modules.forEach {
            RubyClassHierarchyWithCaching.loadFromSystemDirectory(it)
        }

    }
}