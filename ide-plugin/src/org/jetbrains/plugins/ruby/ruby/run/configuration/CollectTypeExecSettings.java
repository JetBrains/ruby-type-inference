package org.jetbrains.plugins.ruby.ruby.run.configuration;

import com.intellij.openapi.util.Key;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CollectTypeExecSettings {

    @NotNull
    public static final String ARG_SCANNER_ENABLED_KEY = "collectTypeArgScannerEnabled";
    @NotNull
    public static final String TYPE_TRACKER_ENABLED_KEY = "collectTypeTypeTrackerEnabled";
    @NotNull
    public static final String STATE_TRACKER_ENABLED_KEY = "collectTypeStateTrackerEnabled";
    @NotNull
    public static final String STATE_TRACKER_PATH_KEY = "collectTypeStateTrackerPath";


    @NotNull
    private static final Key<CollectTypeExecSettings> COLLECT_TYPE_EXEC_SETTINGS = new Key<>("CollectTypeExecSettings");

    private boolean myArgScannerEnabled;
    private boolean myTypeTrackerEnabled;
    private boolean myStateTrackerEnabled;
    @Nullable
    private String myStateTrackerPath;

    public boolean isArgScannerEnabled() {
        return myArgScannerEnabled;
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

    public boolean isStateTrackerEnabled() {
        return myStateTrackerEnabled;
    }

    public void setStateTrackerEnabled(boolean myStateTrackerEnabled) {
        this.myStateTrackerEnabled = myStateTrackerEnabled;
    }

    @Nullable
    public String getStateTrackerPath() {
        return myStateTrackerPath;
    }

    public void setStateTrackerPath(final @Nullable String myStateTrackerPath) {
        this.myStateTrackerPath = myStateTrackerPath;
    }



    @NotNull
    public static CollectTypeExecSettings getFrom(@NotNull final AbstractRubyRunConfiguration configuration) {
        final CollectTypeExecSettings data = configuration.getCopyableUserData(COLLECT_TYPE_EXEC_SETTINGS);
        return data != null ? data : createSettings(false, false,
                false, null);
    }

    public static void putTo(@NotNull final AbstractRubyRunConfiguration configuration,
                             @NotNull final CollectTypeExecSettings settings) {
        configuration.putCopyableUserData(COLLECT_TYPE_EXEC_SETTINGS, settings);
    }

    public static CollectTypeExecSettings createSettings(final boolean argScannerEnabled,
                                                         final boolean typeTrackerEnabled,
                                                         final boolean stateTrackerEnabled,
                                                         final String stateTrackerPath
    ) {
        final CollectTypeExecSettings settings = new CollectTypeExecSettings();
        settings.setArgScannerEnabled(argScannerEnabled);
        settings.setTypeTrackerEnabled(typeTrackerEnabled);
        settings.setStateTrackerEnabled(stateTrackerEnabled);
        settings.setStateTrackerPath(stateTrackerPath);
        return settings;
    }

    public static CollectTypeExecSettings readExternal(@NotNull final Element element) {
        return createSettings(Boolean.valueOf(element.getAttributeValue(ARG_SCANNER_ENABLED_KEY)),
                              Boolean.valueOf(element.getAttributeValue(TYPE_TRACKER_ENABLED_KEY)),
                              Boolean.valueOf(element.getAttributeValue(STATE_TRACKER_ENABLED_KEY)),
                              element.getAttributeValue(STATE_TRACKER_PATH_KEY));
    }

    public void writeExternal(@NotNull final Element element) {
        element.setAttribute(ARG_SCANNER_ENABLED_KEY, String.valueOf(isArgScannerEnabled()));
        element.setAttribute(TYPE_TRACKER_ENABLED_KEY, String.valueOf(isTypeTrackerEnabled()));
        element.setAttribute(STATE_TRACKER_ENABLED_KEY, String.valueOf(isStateTrackerEnabled()));
        element.setAttribute(STATE_TRACKER_PATH_KEY, getStateTrackerPath());
    }
}
