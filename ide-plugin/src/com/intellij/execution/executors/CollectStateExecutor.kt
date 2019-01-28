package com.intellij.execution.executors

import com.intellij.execution.Executor
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowId
import javax.swing.Icon

class CollectStateExecutor : Executor() {

    private val myIcon = AllIcons.General.GearPlain

    override fun getToolWindowId(): String {
        return ToolWindowId.RUN
    }

    override fun getToolWindowIcon(): Icon {
        return myIcon
    }

    override fun getIcon(): Icon {
        return myIcon
    }

    override fun getDisabledIcon(): Icon? {
        return null
    }

    override fun getDescription(): String {
        return "Run selected configuration with collecting state"
    }

    override fun getActionName(): String {
        return "CollectState"
    }

    override fun getId(): String {
        return EXECUTOR_ID
    }

    override fun getStartActionText(): String {
        return "Run with Collecting State"
    }

    override fun getContextActionId(): String {
        return "RunCollectState"
    }

    override fun getHelpId(): String? {
        return null
    }

    override fun getStartActionText(configurationName: String): String {
        val name = escapeMnemonicsInConfigurationName(
                StringUtil.first(configurationName, 30, true))
        return "Run" + (if (StringUtil.isEmpty(name)) "" else " '$name'") + " with Collecting State"
    }

    private fun escapeMnemonicsInConfigurationName(configurationName: String): String {
        return configurationName.replace("_", "__")
    }

    companion object {
        val EXECUTOR_ID = "CollectState"
    }
}