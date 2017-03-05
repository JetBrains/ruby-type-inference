package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

public class RMethodInfo {
    @NotNull
    private final String myMethodName;
    @NotNull
    private final String myReceiverName;
    @NotNull
    private final RVisibility myVisibility;

    @NotNull
    private final GemInfo myGemInfo;

    public RMethodInfo(@NotNull final String methodName,
                       @NotNull final String receiverName,
                       @NotNull final RVisibility visibility,
                       @NotNull final GemInfo gemInfo) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName;
        this.myVisibility = visibility;
        this.myGemInfo = gemInfo;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RMethodInfo that = (RMethodInfo) o;

        return myMethodName.equals(that.myMethodName) &&
                myReceiverName.equals(that.myReceiverName) &&
                myVisibility.equals(that.myVisibility) &&
                myGemInfo.equals(that.myGemInfo);
    }

    public String toString() {
        return "MethodName: " + this.getMethodName() + "\n" +
                "ReceiverName: " + this.getReceiverName() + "\n" +
                "Gem: " + this.getGemInfo().toString() + "\n" +
                "Visibility: " + this.getVisibility();
    }

    public String getMethodName() {
        return this.myMethodName;
    }

    public String getReceiverName() {
        return this.myReceiverName;
    }

    public GemInfo getGemInfo() {
        return this.myGemInfo;
    }

    public RVisibility getVisibility() {
        return this.myVisibility;
    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myGemInfo.hashCode();
        result = 31 * result + myVisibility.hashCode();
        return result;
    }
}
