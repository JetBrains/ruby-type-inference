package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

public final class GemInfo {
    public static final GemInfo NONE = new GemInfo("", "");

    @NotNull
    private final String myName;
    @NotNull
    private final String myVersion;

    public GemInfo(@NotNull String myName, @NotNull String myVersion) {
        this.myName = myName;
        this.myVersion = myVersion;
    }

    public String getName() {
        return myName;
    }

    public String getVersion() {
        return myVersion;
    }

    public String toString() {
        return this.getName() + this.getVersion();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GemInfo gemInfo = (GemInfo) o;

        if (!myName.equals(gemInfo.myName)) return false;
        return myVersion.equals(gemInfo.myVersion);
    }

    @Override
    public int hashCode() {
        int result = myName.hashCode();
        result = 31 * result + myVersion.hashCode();
        return result;
    }
}
