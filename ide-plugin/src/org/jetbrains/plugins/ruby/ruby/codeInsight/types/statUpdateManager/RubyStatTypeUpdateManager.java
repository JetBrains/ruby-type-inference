package org.jetbrains.plugins.ruby.ruby.codeInsight.types.statUpdateManager;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class RubyStatTypeUpdateManager {
    public abstract void updateLocalStat(@NotNull final Project project, @NotNull final Module module);
    public abstract void uploadCollectedStat();
}
