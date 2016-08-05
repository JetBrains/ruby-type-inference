package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;

import java.util.ArrayList;
import java.util.List;

public class RSignature {
    private final String myMethodName;
    private final String myReceiverName;
    private final List<String> myArgsTypeName;
    private final List<ArgumentInfo> myArgsInfo;

    public RSignature(@NotNull final String methodName,
                      @Nullable final String receiverName,
                      @NotNull final List<String> argsTypeName,
                      @Nullable final List<ArgumentInfo> argsInfo) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName != null ? receiverName : CoreTypes.Object;
        this.myArgsTypeName = argsTypeName;
        this.myArgsInfo = argsInfo != null ? argsInfo : new ArrayList<>();
    }

    @NotNull
    public String getMethodName() {
        return myMethodName;
    }

    @NotNull
    public String getReceiverName() {
        return myReceiverName;
    }

    @NotNull
    public List<String> getArgsTypeName() {
        return myArgsTypeName;
    }

    @NotNull
    public List<ArgumentInfo> getArgsInfo() {
        return myArgsInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignature that = (RSignature) o;

        if (!myMethodName.equals(that.myMethodName)) return false;
        if (!myReceiverName.equals(that.myReceiverName)) return false;
        return myArgsTypeName.equals(that.myArgsTypeName);
    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        return result;
    }
}
