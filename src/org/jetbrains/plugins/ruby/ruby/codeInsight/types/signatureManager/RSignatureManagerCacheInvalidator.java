package org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

public class RSignatureManagerCacheInvalidator implements ApplicationComponent {
    @Override
    public void initComponent() {
//        final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
//        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
//            @Override
//            public void processStarted(@NotNull String executorId,
//                                       @NotNull ExecutionEnvironment env,
//                                       @NotNull ProcessHandler handler) {
//                if (env.getExecutor() instanceof CollectTypeExecutor) {
//                    final RSignatureManager signatureManager;
//                    try {
//                        signatureManager = SqliteRSignatureManager.getInstance();
//                    } catch (SQLException | ClassNotFoundException | FileNotFoundException e) {
//                        return;
//                    }
//
//                    ProxyCacheRSignatureManager.getInstance(env.getProject(), signatureManager).invalidateAll();
//                }
//            }
//        });
//
//        final ProjectManager projectManager = ProjectManager.getInstance();
//        projectManager.addProjectManagerListener(new ProjectManagerAdapter() {
//            @Override
//            public void projectClosed(final Project project) {
//                final RSignatureManager signatureManager;
//                try {
//                    signatureManager = SqliteRSignatureManager.getInstance();
//                } catch (SQLException | ClassNotFoundException | FileNotFoundException e) {
//                    return;
//                }
//
//                ProxyCacheRSignatureManager.getInstance(project, signatureManager).invalidateAll();
//            }
//        });
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
