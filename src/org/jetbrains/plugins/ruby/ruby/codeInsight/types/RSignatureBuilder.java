package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;

import java.util.List;

class RSignatureBuilder {
    private String methodName;
    private String receiverName;
    private Visibility visibility = Visibility.PUBLIC;
    private List<ParameterInfo> argsInfo;
    private List<String> argsTypeName;
    private String gemName = "";
    private String gemVersion = "";

    RSignatureBuilder setMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    RSignatureBuilder setReceiverName(String receiverName) {
        this.receiverName = receiverName;
        return this;
    }

    RSignatureBuilder setVisibility(Visibility visibility) {
        this.visibility = visibility;
        return this;
    }

    RSignatureBuilder setArgsInfo(List<ParameterInfo> argsInfo) {
        this.argsInfo = argsInfo;
        return this;
    }

    RSignatureBuilder setArgsTypeName(List<String> argsTypeName) {
        this.argsTypeName = argsTypeName;
        return this;
    }

    public RSignatureBuilder setGemName(String gemName) {
        this.gemName = gemName;
        return this;
    }

    public RSignatureBuilder setGemVersion(String gemVersion) {
        this.gemVersion = gemVersion;
        return this;
    }

    RSignature build() {
        return new RSignature(methodName, receiverName, visibility, argsInfo, argsTypeName, gemName, gemVersion);
    }
}
