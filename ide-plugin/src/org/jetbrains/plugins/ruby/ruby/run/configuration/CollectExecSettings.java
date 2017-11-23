package org.jetbrains.plugins.ruby.ruby.run.configuration;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CollectExecSettings {

    @NotNull
    private static final Key<CollectExecSettings> COLLECT_TYPE_EXEC_SETTINGS = new Key<>("CollectTypeExecSettings");

    private boolean myArgScannerEnabled;
    private boolean myTypeTrackerEnabled;
    private boolean myStateTrackerEnabled;
    private boolean myReturnTypeTrackerEnabled;
    @Nullable
    private String myOutputDirectory;

    public boolean isArgScannerEnabled() {
        return myArgScannerEnabled;
    }

    public boolean isStateTrackerEnabled() {
        return myStateTrackerEnabled;
    }

    public void setStateTrackerEnabled(boolean myStateTrackerEnabled) {
        this.myStateTrackerEnabled = myStateTrackerEnabled;
    }

    public boolean isReturnTypeTrackerEnabled() {
        return myReturnTypeTrackerEnabled;
    }

    public void setReturnTypeTrackerEnabled(boolean myReturnTypeTrackerEnabled) {
        this.myReturnTypeTrackerEnabled = myReturnTypeTrackerEnabled;
    }

    public void setArgScannerEnabled(boolean myArgScannerEnabled) {
        this.myArgScannerEnabled = myArgScannerEnabled;
    }

    public boolean isTypeTrackerEnabled() {
        return myTypeTrackerEnabled;
    }

    public void setTypeTrackerEnabled(boolean myTypeTrackerEnabled) {
        this.myTypeTrackerEnabled = myTypeTrackerEnabled;
    }

    @Nullable
    public String getOutputDirectory() {
        return myOutputDirectory;
    }

    public void setReturnTypeTrackerPath(final @Nullable String path) {
        myOutputDirectory = path;
    }

    @NotNull
    public static CollectExecSettings getFrom(@NotNull final AbstractRubyRunConfiguration configuration) {
        final CollectExecSettings data = configuration.getCopyableUserData(COLLECT_TYPE_EXEC_SETTINGS);
        return data != null ? data : createSettings(false, false, false, false, null);
    }

    public static void putTo(@NotNull final AbstractRubyRunConfiguration configuration,
                             @NotNull final CollectExecSettings settings) {
        configuration.putCopyableUserData(COLLECT_TYPE_EXEC_SETTINGS, settings);
    }

    public static CollectExecSettings createSettings(final boolean argScannerEnabled,
                                                         final boolean typeTrackerEnabled,
                                                         final boolean returnTypeTrackerEnabled,
                                                         final boolean stateTrackerEnabled,
                                                         final String tempDirectoryPath
    ) {
        final CollectExecSettings settings = new CollectExecSettings();
        settings.setArgScannerEnabled(argScannerEnabled);
        settings.setTypeTrackerEnabled(typeTrackerEnabled);
        settings.setReturnTypeTrackerEnabled(returnTypeTrackerEnabled);
        settings.setReturnTypeTrackerPath(tempDirectoryPath);
        settings.setStateTrackerEnabled(stateTrackerEnabled);

        return settings;
    }

}
