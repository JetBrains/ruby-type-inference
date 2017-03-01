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
    private RVisibility myVisibility = RVisibility.PUBLIC;
    @NotNull
    private List<ParameterInfo> myArgsInfo = new ArrayList<>();
    @NotNull
    private List<String> myArgsTypeName = new ArrayList<>();
    @NotNull
    private String myGemName = "";
    @NotNull
    private String myGemVersion = "";
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
    public RSignatureBuilder setVisibility(@NotNull final RVisibility visibility) {
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

    @NotNull
    public RSignatureBuilder setGemName(@NotNull final String gemName) {
        myGemName = gemName;
        return this;
    }

    @NotNull
    public RSignatureBuilder setGemVersion(@NotNull final String gemVersion) {
        myGemVersion = gemVersion;
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
        return new RSignature(myMethodName, myReceiverName, myVisibility, myArgsInfo, myArgsTypeName,
                              myGemName, myGemVersion, myReturnTypeName, myIsLocal);
    }
}
