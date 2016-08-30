package org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.CoreTypes;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;

import java.util.List;

public class RSignatureBuilder {
    @NotNull
    private final String myMethodName;
    @NotNull
    private String myReceiverName = CoreTypes.Object;
    @NotNull
    private Visibility myVisibility = Visibility.PUBLIC;
    @NotNull
    private List<ParameterInfo> myArgsInfo = ContainerUtilRt.emptyList();
    @NotNull
    private List<String> myArgsTypeName = ContainerUtilRt.emptyList();
    @NotNull
    private String myGemName = "";
    @NotNull
    private String myGemVersion = "";
    @NotNull
    private String myReturnTypeName = CoreTypes.Object;

    public RSignatureBuilder(@NotNull final String myMethodName) {
        this.myMethodName = myMethodName;
    }

    @NotNull
    public RSignatureBuilder setReceiverName(@NotNull final String receiverName) {
        this.myReceiverName = receiverName;
        return this;
    }

    @NotNull
    public RSignatureBuilder setVisibility(@NotNull final Visibility visibility) {
        this.myVisibility = visibility;
        return this;
    }

    @NotNull
    public RSignatureBuilder setArgsInfo(@NotNull final List<ParameterInfo> argsInfo) {
        this.myArgsInfo = argsInfo;
        return this;
    }

    @NotNull
    public RSignatureBuilder setArgsTypeName(@NotNull final List<String> argsTypeName) {
        this.myArgsTypeName = argsTypeName;
        return this;
    }

    @NotNull
    public RSignatureBuilder setGemName(@NotNull final String gemName) {
        this.myGemName = gemName;
        return this;
    }

    @NotNull
    public RSignatureBuilder setGemVersion(@NotNull final String gemVersion) {
        this.myGemVersion = gemVersion;
        return this;
    }

    @NotNull
    public RSignatureBuilder setReturnTypeName(@NotNull final String returnTypeName) {
        this.myReturnTypeName = returnTypeName;
        return this;
    }

    @NotNull
    public RSignature build() {
        return new RSignature(myMethodName, myReceiverName, myVisibility, myArgsInfo, myArgsTypeName,
                              myGemName, myGemVersion, myReturnTypeName);
    }
}
