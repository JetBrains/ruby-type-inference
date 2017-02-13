package org.jetbrains.ruby.runtime.signature;

import org.jetbrains.annotations.NotNull;

public class RestrictedParameterInfo {
    @NotNull
    private final String myName;
    @NotNull
    private final Type myType;
    @NotNull
    private final String myDefaultValueTypeName;

    public RestrictedParameterInfo(@NotNull final String name, @NotNull final Type type,
                                   @NotNull final String defaultValueTypeName) {
        this.myName = name;
        this.myType = type;
        this.myDefaultValueTypeName = defaultValueTypeName;
    }

    @NotNull
    public String getName() {
        return myName;
    }

    @NotNull
    public Type getType() {
        return myType;
    }

    @NotNull
    public String getDefaultValueTypeName() {
        return myDefaultValueTypeName;
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
