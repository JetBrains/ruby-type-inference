package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RSignature {
    private final String myMethodName;
    private final List<String> myArgsName;

    public RSignature(@NotNull String methodName, @NotNull List<String> argsName) {
        this.myMethodName = methodName;
        this.myArgsName = argsName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignature that = (RSignature) o;

        return myMethodName.equals(that.myMethodName) && myArgsName.equals(that.myArgsName);

    }

    @Override
    public int hashCode() {
        return 31 * myMethodName.hashCode() + myArgsName.hashCode();
    }
}
