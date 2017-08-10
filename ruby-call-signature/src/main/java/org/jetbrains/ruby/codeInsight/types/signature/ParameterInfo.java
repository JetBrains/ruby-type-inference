package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterInfo {
    // TODO investigate nullability
    @Nullable
    private final String myName;
    @NotNull
    private final ParameterInfo.Type myModifier;

    public ParameterInfo(@Nullable final String name, @NotNull final Type modifier) {
        this.myName = name;
        this.myModifier = modifier;
    }

    @Nullable
    public String getName() {
        return this.myName;
    }

    @NotNull
    public ParameterInfo.Type getModifier() {
        return this.myModifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ParameterInfo that = (ParameterInfo) o;

        //noinspection SimplifiableIfStatement
        if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;
        return myModifier == that.myModifier;
    }

    @Override
    public int hashCode() {
        int result = myName != null ? myName.hashCode() : 0;
        result = 31 * result + myModifier.hashCode();
        return result;
    }

    public enum Type {
        REQ,
        OPT,
        REST,
        KEYREQ,
        KEY,
        KEYREST,
        BLOCK,
    }
}
