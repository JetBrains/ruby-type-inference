package org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.executors.CollectTypeExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class RSignatureManagerCacheInvalidator implements ApplicationComponent {
    @Override
    public void initComponent() {
        final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId,
                                       @NotNull ExecutionEnvironment env,
                                       @NotNull ProcessHandler handler) {
                if (env.getExecutor() instanceof CollectTypeExecutor) {
                    final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
                    if (signatureManager != null) {
                        ProxyCacheRSignatureManager.getInstance(env.getProject(), signatureManager).invalidateAll();
                    }
                }
            }
        });

        final ProjectManager projectManager = ProjectManager.getInstance();
        projectManager.addProjectManagerListener(new ProjectManagerAdapter() {
            @Override
            public void projectClosed(final Project project) {
                final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
                if (signatureManager != null) {
                    ProxyCacheRSignatureManager.getInstance(project, signatureManager).invalidateAll();
                }
            }
        });
    }

    @Override
    public void disposeComponent() {
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "RSignatureManagerCacheInvalidator";
    }
}
