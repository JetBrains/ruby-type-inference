package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;

import java.util.HashMap;
import java.util.Map;

public class RSignatureContractNode implements SignatureNode {

    @NotNull
    private final Map<ContractTransition, SignatureNode> myTransitions;

    public RSignatureContractNode() {
        myTransitions = new HashMap<>();
    }

    public void addLink(final @NotNull ContractTransition transition, @NotNull SignatureNode arrivalNode) {
        myTransitions.put(transition, arrivalNode);
    }

    @NotNull
    @Override
    public Map<ContractTransition, SignatureNode> getTransitions() {
        return myTransitions;
    }
}
