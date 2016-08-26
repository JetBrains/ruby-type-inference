package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;

import java.util.List;

class RSignatureBuilder {
    @Nullable
    private String methodName;
    @Nullable
    private String receiverName;
    @NotNull
    private Visibility visibility = Visibility.PUBLIC;
    @Nullable
    private List<ParameterInfo> argsInfo;
    @Nullable
    private List<String> argsTypeName;
    @NotNull
    private String gemName = "";
    @NotNull
    private String gemVersion = "";

    @NotNull
    RSignatureBuilder setMethodName(@NotNull final String methodName) {
        this.methodName = methodName;
        return this;
    }

    @NotNull
    RSignatureBuilder setReceiverName(@NotNull final String receiverName) {
        this.receiverName = receiverName;
        return this;
    }

    @NotNull
    RSignatureBuilder setVisibility(@NotNull final Visibility visibility) {
        this.visibility = visibility;
        return this;
    }

    @NotNull
    RSignatureBuilder setArgsInfo(@NotNull final List<ParameterInfo> argsInfo) {
        this.argsInfo = argsInfo;
        return this;
    }

    @NotNull
    RSignatureBuilder setArgsTypeName(@NotNull final List<String> argsTypeName) {
        this.argsTypeName = argsTypeName;
        return this;
    }

    @NotNull
    RSignatureBuilder setGemName(@NotNull final String gemName) {
        this.gemName = gemName;
        return this;
    }

    @NotNull
    RSignatureBuilder setGemVersion(@NotNull final String gemVersion) {
        this.gemVersion = gemVersion;
        return this;
    }

    @NotNull
    RSignature build() {
        return new RSignature(methodName, receiverName, visibility, argsInfo, argsTypeName, gemName, gemVersion);
    }
}
