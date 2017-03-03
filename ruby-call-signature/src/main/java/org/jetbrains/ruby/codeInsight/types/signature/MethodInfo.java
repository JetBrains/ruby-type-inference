package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

public final class MethodInfo {
    @NotNull
    private final ClassInfo myClassInfo;
    @NotNull
    private final String myName;
    @NotNull
    private final RVisibility myVisibility;

    public MethodInfo(@NotNull ClassInfo classInfo, @NotNull String name, @NotNull RVisibility visibility) {
        myClassInfo = classInfo;
        myName = name;
        myVisibility = visibility;
    }

    @NotNull
    public ClassInfo getClassInfo() {
        return myClassInfo;
    }

    @NotNull
    public String getName() {
        return myName;
    }

    @NotNull
    public RVisibility getVisibility() {
        return myVisibility;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MethodInfo that = (MethodInfo) o;

        if (!myClassInfo.equals(that.myClassInfo)) return false;
        if (!myName.equals(that.myName)) return false;
        return myVisibility == that.myVisibility;
    }

    @Override
    public int hashCode() {
        int result = myClassInfo.hashCode();
        result = 31 * result + myName.hashCode();
        result = 31 * result + myVisibility.hashCode();
        return result;
    }

    public enum RVisibility {
        PRIVATE(0, "PRIVATE"),
        PROTECTED(1, "PROTECTED"),
        PUBLIC(2, "PUBLIC");

        @NotNull
        private final String myPresentableName;
        private final byte myValue;

        RVisibility(final int value, @NotNull final String presentableName) {
            myValue = (byte) value;
            myPresentableName = presentableName;
        }

        @NotNull
        public String getPresentableName() {
            return myPresentableName;
        }
    }
}
