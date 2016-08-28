package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;

import java.util.List;

class RSignatureBuilder {
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

    RSignatureBuilder(@NotNull final String myMethodName) {
        this.myMethodName = myMethodName;
    }

    @NotNull
    RSignatureBuilder setReceiverName(@NotNull final String receiverName) {
        this.myReceiverName = receiverName;
        return this;
    }

    @NotNull
    RSignatureBuilder setVisibility(@NotNull final Visibility visibility) {
        this.myVisibility = visibility;
        return this;
    }

    @NotNull
    RSignatureBuilder setArgsInfo(@NotNull final List<ParameterInfo> argsInfo) {
        this.myArgsInfo = argsInfo;
        return this;
    }

    @NotNull
    RSignatureBuilder setArgsTypeName(@NotNull final List<String> argsTypeName) {
        this.myArgsTypeName = argsTypeName;
        return this;
    }

    @NotNull
    RSignatureBuilder setGemName(@NotNull final String gemName) {
        this.myGemName = gemName;
        return this;
    }

    @NotNull
    RSignatureBuilder setGemVersion(@NotNull final String gemVersion) {
        this.myGemVersion = gemVersion;
        return this;
    }

    @NotNull
    RSignature build() {
        return new RSignature(myMethodName, myReceiverName, myVisibility, myArgsInfo, myArgsTypeName,
                              myGemName, myGemVersion);
    }
}
