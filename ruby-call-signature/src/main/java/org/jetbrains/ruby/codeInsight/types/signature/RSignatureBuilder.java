package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RSignatureBuilder {
    @NotNull
    private final String myMethodName;
    @NotNull
    private String myReceiverName = "Object";
    @NotNull
    private MethodInfo.RVisibility myVisibility = MethodInfo.RVisibility.PUBLIC;
    @NotNull
    private List<ParameterInfo> myArgsInfo = new ArrayList<>();
    @NotNull
    private List<String> myArgsTypeName = new ArrayList<>();
    @NotNull
    private GemInfo myGemInfo = GemInfo.NONE;
    @NotNull
    private String myReturnTypeName = "Object";
    private boolean myIsLocal = true;

    public RSignatureBuilder(@NotNull final String methodName) {
        this.myMethodName = methodName;
    }

    @NotNull
    public RSignatureBuilder setReceiverName(@NotNull final String receiverName) {
        myReceiverName = receiverName;
        return this;
    }

    @NotNull
    public RSignatureBuilder setVisibility(@NotNull final MethodInfo.RVisibility visibility) {
        myVisibility = visibility;
        return this;
    }

    @NotNull
    public RSignatureBuilder setArgsInfo(@NotNull final List<ParameterInfo> argsInfo) {
        myArgsInfo = argsInfo;
        return this;
    }

    @NotNull
    public RSignatureBuilder setArgsTypeName(@NotNull final List<String> argsTypeName) {
        myArgsTypeName = argsTypeName;
        return this;
    }

    public RSignatureBuilder setGemInfo(@NotNull GemInfo gemInfo) {
        myGemInfo = gemInfo;
        return this;
    }

    @NotNull
    public RSignatureBuilder setReturnTypeName(@NotNull final String returnTypeName) {
        myReturnTypeName = returnTypeName;
        return this;
    }

    @NotNull
    public RSignatureBuilder setIsLocal(final boolean isLocal) {
        myIsLocal = isLocal;
        return this;
    }

    @NotNull
    public RSignature build() {
        return new RSignature(new MethodInfo(new ClassInfo(myReceiverName), myMethodName, myVisibility),
                myArgsInfo, myArgsTypeName, myGemInfo, myReturnTypeName, myIsLocal);
    }
}
