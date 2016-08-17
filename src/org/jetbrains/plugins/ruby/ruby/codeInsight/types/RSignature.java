package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;

import java.util.Collections;
import java.util.List;

class RSignature {
    @NotNull
    private final String myMethodName;
    @NotNull
    private final String myReceiverName;
    @NotNull
    private final Visibility myVisibility;
    @NotNull
    private final List<ArgumentInfo> myArgsInfo;
    @NotNull
    private final List<String> myArgsTypeName;

    RSignature(@NotNull final String methodName, @NotNull final String receiverName, @NotNull final Visibility visibility,
               @NotNull final List<ArgumentInfo> argsInfo, @NotNull final List<String> argsTypeName) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName;
        this.myVisibility = visibility;
        this.myArgsInfo = Collections.unmodifiableList(argsInfo);
        this.myArgsTypeName = Collections.unmodifiableList(argsTypeName);
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
    List<ArgumentInfo> getArgsInfo() {
        return myArgsInfo;
    }

    @NotNull
    List<String> getArgsTypeName() {
        return myArgsTypeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignature that = (RSignature) o;

        return myMethodName.equals(that.myMethodName) &&
               myReceiverName.equals(that.myReceiverName) &&
               myArgsTypeName.equals(that.myArgsTypeName);
    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myArgsInfo.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        return result;
    }
}
