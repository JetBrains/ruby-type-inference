package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

public class ParameterInfo {
    @NotNull
    private final String myName;
    @NotNull
    private final ParameterInfo.Type myModifier;

    public ParameterInfo(@NotNull final String name, @NotNull final Type modifier) {
        myName = name;
        myModifier = modifier;
    }

    @NotNull
    public String getName() {
        return myName;
    }

    @NotNull
    public ParameterInfo.Type getModifier() {
        return myModifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ParameterInfo that = (ParameterInfo) o;

        //noinspection SimplifiableIfStatement
        if (!myName.equals(that.myName)) return false;
        return myModifier == that.myModifier;
    }

    @Override
    public int hashCode() {
        int result = myName.hashCode();
        result = 31 * result + myModifier.hashCode();
        return result;
    }

    public enum Type {
        REQ,
        OPT,
        POST,
        REST,
        KEYREQ,
        KEY,
        KEYREST,
        BLOCK,
    }
}
