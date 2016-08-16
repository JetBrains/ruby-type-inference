package com.intellij.execution.executors;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CollectTypeExecutor extends Executor {
    @NotNull
    public static final String EXECUTOR_ID = "CollectType";

    @Override
    public String getToolWindowId() {
        return ToolWindowId.RUN;
    }

    @Override
    public Icon getToolWindowIcon() {
        return AllIcons.General.RunWithCoverage;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return AllIcons.General.RunWithCoverage;
    }

    @Nullable
    @Override
    public Icon getDisabledIcon() {
        return null;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Run selected configuration with collecting type info";
    }

    @NotNull
    @Override
    public String getActionName() {
        return "CollectType";
    }

    @NotNull
    @Override
    public String getId() {
        return EXECUTOR_ID;
    }

    @NotNull
    public String getStartActionText() {
        return "Run with Collecting Type Info";
    }

    @NotNull
    @Override
    public String getContextActionId() {
        return "RunCollectType";
    }

    @Nullable
    @Override
    public String getHelpId() {
        return null;
    }

    @NotNull
    @Override
    public String getStartActionText(@Nullable final String configurationName) {
        final String name = configurationName != null
                ? escapeMnemonicsInConfigurationName(StringUtil.first(configurationName, 30, true))
                : null;
        return "Run" + (StringUtil.isEmpty(name) ? "" :  " '" + name + "'") + " with Collecting Type Info";
    }

    @NotNull
    private static String escapeMnemonicsInConfigurationName(@NotNull final String configurationName) {
        return configurationName.replace("_", "__");
    }
}
