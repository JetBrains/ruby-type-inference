package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RSignature {
    @NotNull
    private final MethodInfo myMethodInfo;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<String> myArgsTypeName;
    @NotNull
    private final GemInfo myGemInfo;
    @NotNull
    private String myReturnTypeName;

    private final boolean myIsLocal;

    public RSignature(@NotNull final MethodInfo methodInfo,
                      @NotNull final List<ParameterInfo> argsInfo,
                      @NotNull final List<String> argsTypeName,
                      @NotNull final GemInfo gemInfo,
                      @NotNull final String returnTypeName,
                      final boolean isLocal) {
        myMethodInfo = methodInfo;
        myArgsInfo = argsInfo;
        myArgsTypeName = argsTypeName;
        myGemInfo = gemInfo;
        myReturnTypeName = returnTypeName;
        myIsLocal = isLocal;
    }

    @NotNull
    public MethodInfo getMethodInfo() {
        return myMethodInfo;
    }

    @NotNull
    public List<ParameterInfo> getArgsInfo() {
        return myArgsInfo;
    }

    @NotNull
    public List<String> getArgsTypeName() {
        return myArgsTypeName;
    }

    @NotNull
    public GemInfo getGemInfo() {
        return myGemInfo;
    }

    @NotNull
    public String getReturnTypeName() {
        return myReturnTypeName;
    }

    public void setReturnTypeName(@NotNull final String returnTypeName) {
        this.myReturnTypeName = returnTypeName;
    }

    public boolean isLocal() {
        return myIsLocal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RSignature that = (RSignature) o;

        if (myIsLocal != that.myIsLocal) return false;
        if (!myMethodInfo.equals(that.myMethodInfo)) return false;
        if (!myArgsInfo.equals(that.myArgsInfo)) return false;
        if (!myArgsTypeName.equals(that.myArgsTypeName)) return false;
        if (!myGemInfo.equals(that.myGemInfo)) return false;
        return myReturnTypeName.equals(that.myReturnTypeName);
    }

    @Override
    public int hashCode() {
        int result = myMethodInfo.hashCode();
        result = 31 * result + myArgsInfo.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        result = 31 * result + myGemInfo.hashCode();
        result = 31 * result + myReturnTypeName.hashCode();
        result = 31 * result + (myIsLocal ? 1 : 0);
        return result;
    }
}
