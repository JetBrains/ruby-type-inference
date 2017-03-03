package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

public final class ClassInfo {
    @NotNull
    private final String myClassFQN;

    public ClassInfo(@NotNull String classFQN) {
        myClassFQN = classFQN;
    }

    @NotNull
    public String getClassFQN() {
        return myClassFQN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ClassInfo classInfo = (ClassInfo) o;

        return myClassFQN.equals(classInfo.myClassFQN);
    }

    @Override
    public int hashCode() {
        return myClassFQN.hashCode();
    }
}
