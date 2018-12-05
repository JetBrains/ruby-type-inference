package com.intellij.execution.executors;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URL;

public class RunWithTypeTrackerExecutor extends Executor {
    @NotNull
    public static final String EXECUTOR_ID = "RunWithTypeTracker";

    @NotNull
    private final Icon myIcon;

    public RunWithTypeTrackerExecutor() {
        final URL iconURL = RunWithTypeTrackerExecutor.class.getClassLoader().getResource(
                UIUtil.isUnderDarcula() ? "runWithTypeTracker_dark.svg" : "runWithTypeTracker.svg");
        final Icon icon = IconLoader.findIcon(iconURL);
        myIcon = icon != null ? icon : AllIcons.General.Error;
    }

    @Override
    public String getToolWindowId() {
        return ToolWindowId.RUN;
    }

    @Override
    public Icon getToolWindowIcon() {
        return myIcon;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return myIcon;
    }

    @Nullable
    @Override
    public Icon getDisabledIcon() {
        return null;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Run selected configuration with Type Tracker";
    }

    @NotNull
    @Override
    public String getActionName() {
        return "Run with Type Tracker";
    }

    @NotNull
    @Override
    public String getId() {
        return EXECUTOR_ID;
    }

    @NotNull
    public String getStartActionText() {
        return "Run with Type Tracker";
    }

    @NotNull
    @Override
    public String getContextActionId() {
        return "Run with Type Tracker";
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
        return "Run" + (StringUtil.isEmpty(name) ? "" :  " '" + name + "'") + " with Type Tracker";
    }

    @NotNull
    private static String escapeMnemonicsInConfigurationName(@NotNull final String configurationName) {
        return configurationName.replace("_", "__");
    }
}
