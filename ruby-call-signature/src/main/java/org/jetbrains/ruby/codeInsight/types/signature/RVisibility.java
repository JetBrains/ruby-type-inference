package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

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
