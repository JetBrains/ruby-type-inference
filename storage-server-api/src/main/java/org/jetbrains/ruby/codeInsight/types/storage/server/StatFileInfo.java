package org.jetbrains.ruby.codeInsight.types.storage.server;

import org.jetbrains.annotations.NotNull;

public class StatFileInfo {
    @NotNull
    private final String myGemFullName;
    @NotNull
    private final String myGemName;
    @NotNull
    private final String myGemVersion;
    private final long myLastModified;

    public StatFileInfo(@NotNull final String gemFullName, final long lastModified) {
        final int gemVersionIdx = gemFullName.lastIndexOf('-');
        final int fileExtensionIdx = gemFullName.lastIndexOf(".json");
        if (gemVersionIdx == -1 || fileExtensionIdx == -1) {
            throw new IllegalArgumentException("Failed to parse gem name and version: " + gemFullName);
        }

        myGemFullName = gemFullName;
        myGemName = myGemFullName.substring(0, gemVersionIdx);
        myGemVersion = myGemFullName.substring(gemVersionIdx + 1, fileExtensionIdx);
        myLastModified = lastModified;
    }

    @NotNull
    public String getFullGemName() {
        return myGemFullName;
    }

    @NotNull
    public String getGemName() {
        return myGemName;
    }

    @NotNull
    public String getGemVersion() {
        return myGemVersion;
    }

    public long getLastModified() {
        return myLastModified;
    }
}
