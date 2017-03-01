package org.jetbrains.plugins.ruby.ruby.run.configuration;

import com.intellij.openapi.util.Key;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class CollectTypeExecSettings {
    @NotNull
    public static final String COLLECT_TYPE_EXEC_ENABLED = "collectTypeExecEnabled";

    @NotNull
    private static final Key<CollectTypeExecSettings> COLLECT_TYPE_EXEC_SETTINGS = new Key<>("CollectTypeExecSettings");

    public boolean isCollectTypeExecEnabled;

    @NotNull
    public static CollectTypeExecSettings getFrom(@NotNull final AbstractRubyRunConfiguration configuration) {
        final CollectTypeExecSettings data = configuration.getCopyableUserData(COLLECT_TYPE_EXEC_SETTINGS);
        return data != null ? data : createSettings(false);
    }

    public static void putTo(@NotNull final AbstractRubyRunConfiguration configuration,
                             @NotNull final CollectTypeExecSettings settings) {
        configuration.putCopyableUserData(COLLECT_TYPE_EXEC_SETTINGS, settings);
    }

    public static CollectTypeExecSettings createSettings(final boolean enabled) {
        final CollectTypeExecSettings settings = new CollectTypeExecSettings();
        settings.isCollectTypeExecEnabled = enabled;
        return settings;
    }

    public static CollectTypeExecSettings readExternal(@NotNull final Element element) {
        return createSettings(Boolean.valueOf(element.getAttributeValue(COLLECT_TYPE_EXEC_ENABLED)));
    }

    public void writeExternal(@NotNull final Element element) {
        element.setAttribute(COLLECT_TYPE_EXEC_ENABLED, String.valueOf(isCollectTypeExecEnabled));
    }
}
