package signature;

import org.jetbrains.annotations.NotNull;

public enum RestrictedVisibility {
    PRIVATE(0, "PRIVATE"),
    PROTECTED(1, "PROTECTED"),
    PUBLIC(2, "PUBLIC");

    private final String myPresentableName;
    private final byte myValue;

    RestrictedVisibility(final int value, @NotNull final String presentableName) {
        myValue = (byte) value;
        myPresentableName = presentableName;
    }
}
