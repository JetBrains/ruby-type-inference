package org.jetbrains.plugins.ruby.ruby.run.configuration;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.AlarmFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.plugins.ruby.gem.GemDependency;
import org.jetbrains.plugins.ruby.gem.GemInfo;
import org.jetbrains.plugins.ruby.gem.GemInstallUtil;
import org.jetbrains.plugins.ruby.gem.util.GemSearchUtil;
import org.jetbrains.plugins.ruby.ruby.RubyUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.RubyClassHierarchyWithCaching;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.RubyTypeProviderKt;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RunWithTypeTrackerRunConfigurationExtension extends RubyRunConfigurationExtension {
    private static final Logger LOG = Logger.getInstance(RunWithTypeTrackerRunConfigurationExtension.class);
    private static final String ARG_SCANNER_GEM_NAME = "arg_scanner";

    private static final String ARG_SCANNER_REQUIRE_SCRIPT = "arg_scanner/starter";

    private static final String ENABLE_TYPE_TRACKER_KEY = "ARG_SCANNER_ENABLE_TYPE_TRACKER";

    private static final String ENABLE_STATE_TRACKER_KEY = "ARG_SCANNER_ENABLE_STATE_TRACKER";

    private static final String PROJECT_ROOT_KEY = "ARG_SCANNER_PROJECT_ROOT";

    private static final String ARG_SCANNER_PIPE_FILE_PATH_KEY = "ARG_SCANNER_PIPE_FILE_PATH";

    private static final String OUTPUT_DIRECTORY = "ARG_SCANNER_DIR";

    private static final int MAX_RETRY_NO = 15;

    private static final int RETRY_TIMEOUT = 2000;


    @Override
    protected void readExternal(@NotNull final AbstractRubyRunConfiguration runConfiguration,
                                @NotNull final Element element) throws InvalidDataException {

    }

    @Nullable
    @Override
    protected String getEditorTitle() {
        return null;
    }

    @Override
    public boolean isApplicableFor(@NotNull AbstractRubyRunConfiguration<?> configuration) {
        return true;
    }

    @Override
    public boolean isEnabledFor(@NotNull AbstractRubyRunConfiguration<?> applicableConfiguration,
                                @Nullable RunnerSettings runnerSettings) {
        final CollectExecSettings config = CollectExecSettings.getFrom(applicableConfiguration);
        return config.isArgScannerEnabled();
    }

    @Override
    protected void patchCommandLine(@NotNull final AbstractRubyRunConfiguration configuration,
                                    @Nullable final RunnerSettings runnerSettings,
                                    @NotNull final GeneralCommandLine cmdLine,
                                    @NotNull final String runnerId) {
        final Module module = configuration.getModule();
        if (module == null) {
            return;
        }

        String includeKey = getRequireKeyForGem(module, configuration.getSdk(), ARG_SCANNER_GEM_NAME);
        if (includeKey == null) {
            return;
        }

        SignatureServer server = new SignatureServer();

        String pipeFileName = server.runServerAsync(true);

        server.setAfterExitListener(() -> {
            RubyTypeProviderKt.getRegisteredCallInfosCache().clear();
            return null;
        });

        final Map<String, String> env = cmdLine.getEnvironment();
        final String rubyOpt = StringUtil.notNullize(env.get(RubyUtil.RUBYOPT));
        final CollectExecSettings collectTypeSettings = CollectExecSettings.getFrom(configuration);
        if (collectTypeSettings.isTypeTrackerEnabled()) {
            env.put(ENABLE_TYPE_TRACKER_KEY, "1");
        }
        if (collectTypeSettings.isStateTrackerEnabled()) {
            env.put(ENABLE_STATE_TRACKER_KEY, "1");
        }
        if (collectTypeSettings.getOutputDirectory() != null) {
            env.put(OUTPUT_DIRECTORY, collectTypeSettings.getOutputDirectory());
        }
        @SystemIndependent String basePath = configuration.getProject().getBasePath();
        if (basePath != null) {
            env.put(PROJECT_ROOT_KEY, basePath);
        }
        env.put(ARG_SCANNER_PIPE_FILE_PATH_KEY, pipeFileName);

        final String newRubyOpt = rubyOpt + includeKey + " -r" + ARG_SCANNER_REQUIRE_SCRIPT;

        cmdLine.withEnvironment(RubyUtil.RUBYOPT, newRubyOpt);
    }

    @Override
    protected void validateConfiguration(@NotNull AbstractRubyRunConfiguration configuration, boolean isExecution) throws Exception {
        RunConfigurationUtil.inspectSDK(configuration, isExecution);
        final Module module = configuration.getModule();
        final Sdk sdk = configuration.getSdk();
        if (module == null || sdk == null) {
            RunConfigurationUtil.throwExecutionOrRuntimeException("Cannot execute outside of module context", isExecution);
        }

        GemInfo argScannerGem = GemSearchUtil.findGem(module, sdk, ARG_SCANNER_GEM_NAME);
        if (argScannerGem == null) {
            if (isExecution) {
                final int result = Messages.showYesNoDialog(configuration.getProject(),
                        "'arg_scanner' gem is required to collect the data. Do you want to install it?",
                        "Gem Not Found", null);
                if (result == Messages.YES) {
                    final HashMap<GemDependency, String> errors = new HashMap<>();
                    GemInstallUtil.installGemsDependencies(sdk,
                            module,
                            Collections.singleton(GemDependency.create(ARG_SCANNER_GEM_NAME, ">= 0.2.0")),
                            true,
                            false,
                            errors);
                    if (errors.isEmpty()) {
                        // means success
                        return;
                    }
                }
            }

            RunConfigurationUtil.throwExecutionOrRuntimeException("Cannot find required " + ARG_SCANNER_GEM_NAME +
                    " gem in the current SDK", false);
        }
    }

    @Nullable
    private String getRequireKeyForGem(@NotNull Module module, @Nullable Sdk sdk, @NotNull String gemName) {
        final GemInfo gemInfo = GemSearchUtil.findGem(module, sdk, gemName);
        if (gemInfo == null) {
            return null;
        }

        final VirtualFile libFolder = gemInfo.getLibFolder();
        if (libFolder == null) {
            return null;
        }
        return " -I" + libFolder.getPath();
    }

    @Override
    protected void attachToProcess(@NotNull AbstractRubyRunConfiguration configuration,
                                   @NotNull ProcessHandler handler, @Nullable RunnerSettings runnerSettings) {
        final CollectExecSettings settings = CollectExecSettings.getFrom(configuration);
        if (settings.isStateTrackerEnabled()) {
            handler.addProcessListener(
                new ProcessAdapter() {
                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        processStateTrackerResult(settings, configuration);
                    }
                }
            );
        }
    }

    private boolean checkForPidFiles(final @NotNull File directory) {
        File[] listOfFiles = directory.listFiles();
        return listOfFiles != null && Arrays.stream(listOfFiles).anyMatch((it) -> it.getName().endsWith(".pid"));
    }

    private void waitAllProcess(final @NotNull File directory,
                                final @NotNull Runnable task,
                                final @NotNull Disposable parent,
                                int tryNo) {
        if (!checkForPidFiles(directory) || tryNo > MAX_RETRY_NO) {
            task.run();
        } else {
            AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, parent).addRequest(
                    () -> waitAllProcess(directory, task, parent,tryNo + 1), RETRY_TIMEOUT);
        }
    }

    private void processStateTrackerResult(final @NotNull CollectExecSettings settings,
                                           final @NotNull AbstractRubyRunConfiguration configuration) {
        String directoryPath = settings.getOutputDirectory();
        assert directoryPath != null;
        File directory = new File(directoryPath);
        final Module module = configuration.getModule();
        if (module == null) {
            return;
        }
        waitAllProcess(directory, () -> {
            try {
                File[] listOfFiles = directory.listFiles();
                if (listOfFiles == null) {
                    return;
                }

                final List<String> jsons = Arrays.stream(listOfFiles).filter((it) -> it.getName().endsWith("json")).map((it) -> {
                    try {
                        return FileUtil.loadFile(it);
                    } catch (IOException e) {
                        LOG.warn(e);
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList());
                if (jsons.isEmpty()) {
                    return;
                }
                if (settings.isStateTrackerEnabled()) {
                    RubyClassHierarchyWithCaching.Companion.updateAndSaveToSystemDirectory(jsons, module);
                }
            } finally {
                FileUtil.delete(directory);
            }
        },  module, 0);
    }


}
