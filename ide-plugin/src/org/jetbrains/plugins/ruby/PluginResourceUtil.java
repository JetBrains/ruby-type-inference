package org.jetbrains.plugins.ruby;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class PluginResourceUtil {
    private static final String PLUGIN_ID = "org.jetbrains.ruby-runtime-stats";

    private PluginResourceUtil() {
    }

    @NotNull
    public static String getPluginResourcesPath() {
        final IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId(PLUGIN_ID));
        if (plugin == null) {
            throw new AssertionError("Nonsense: this plugin is not registered");
        }
        final File pluginHome = plugin.getPath();
        if (pluginHome == null) {
            throw new AssertionError("Corrupted plugin: could not find home");
        }
        return pluginHome.getPath() + "/";
    }
}
