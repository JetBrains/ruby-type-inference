package org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature;

import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;

public class ParameterInfo {
    @NotNull
    private final String myName;
    @NotNull
    private final Type myType;
    @NotNull
    private final String myDefaultValueTypeName;

    public ParameterInfo(@NotNull final String name, @NotNull final Type type,
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParameterInfo that = (ParameterInfo) o;

        if (!myName.equals(that.myName)) return false;
        if (myType != that.myType) return false;
        return myDefaultValueTypeName.equals(that.myDefaultValueTypeName);

    }

    @Override
    public int hashCode() {
        int result = myName.hashCode();
        result = 31 * result + myType.hashCode();
        result = 31 * result + myDefaultValueTypeName.hashCode();
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

    @NotNull
    public ArgumentInfo toArgumentInfo() {
        return new ArgumentInfo(StringRef.fromString(myName), parameterTypeToArgumentType(myType));
    }

    @NotNull
    private static ArgumentInfo.Type parameterTypeToArgumentType(@NotNull final Type type) {
        switch (type) {
            case REQ:
            case KEYREQ:
                return ArgumentInfo.Type.SIMPLE;
            case OPT:
            case KEY:
                return ArgumentInfo.Type.PREDEFINED;
            case REST:
                return ArgumentInfo.Type.ARRAY;
            case KEYREST:
                return ArgumentInfo.Type.HASH;
            case BLOCK:
                return ArgumentInfo.Type.BLOCK;
            default:
                throw new IllegalArgumentException();
        }
    }
}
