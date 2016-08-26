package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;

import java.util.List;

class RSignature {
    @NotNull
    private final String myMethodName;
    @NotNull
    private final String myReceiverName;
    @NotNull
    private final Visibility myVisibility;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<String> myArgsTypeName;
    @NotNull
    private final String myGemName;
    @NotNull
    private final String myGemVersion;

    RSignature(@NotNull final String methodName, @NotNull final String receiverName, @NotNull final Visibility visibility,
               @NotNull final List<ParameterInfo> argsInfo, @NotNull final List<String> argsTypeName,
               @NotNull final String gemName, @NotNull final String gemVersion) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName;
        this.myVisibility = visibility;
        this.myArgsInfo = argsInfo;
        this.myArgsTypeName = argsTypeName;
        this.myGemName = gemName;
        this.myGemVersion = gemVersion;
    }

    @NotNull
    String getMethodName() {
        return myMethodName;
    }

    @NotNull
    String getReceiverName() {
        return myReceiverName;
    }

    @NotNull
    Visibility getVisibility() {
        return myVisibility;
    }

    @NotNull
    List<ParameterInfo> getArgsInfo() {
        return myArgsInfo;
    }

    @NotNull
    List<String> getArgsTypeName() {
        return myArgsTypeName;
    }

    @NotNull
    String getGemName() {
        return myGemName;
    }

    @NotNull
    String getGemVersion() {
        return myGemVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignature that = (RSignature) o;

        return myMethodName.equals(that.myMethodName) &&
               myReceiverName.equals(that.myReceiverName) &&
               myVisibility == that.myVisibility &&
               myArgsInfo.equals(that.myArgsInfo) &&
               myArgsTypeName.equals(that.myArgsTypeName) &&
               myGemName.equals(that.myGemName) &&
               myGemVersion.equals(that.myGemVersion);

    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myVisibility.hashCode();
        result = 31 * result + myArgsInfo.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        result = 31 * result + myGemName.hashCode();
        result = 31 * result + myGemVersion.hashCode();
        return result;
    }
}
