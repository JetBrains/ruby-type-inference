package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RSignatureContractNode implements SignatureNode {

    @NotNull
    private final Map<ContractTransition, SignatureNode> myTransitions;

    public RSignatureContractNode() {
        myTransitions = new HashMap<>();
    }

    @NotNull
    public RSignatureContractNode goByTransition(ContractTransition transition) {
        return ((RSignatureContractNode) myTransitions.get(transition));
    }

    @NotNull
    public Set<ContractTransition> getTransitionKeys() {
        return myTransitions.keySet();
    }

    public void addLink(final @NotNull ContractTransition transition, @NotNull RSignatureContractNode arrivalNode) {
        myTransitions.put(transition, arrivalNode);
    }

    public boolean containsKey(final @NotNull ContractTransition transition) {
        return myTransitions.keySet().contains(transition);
    }


    @NotNull
    @Override
    public Map<ContractTransition, SignatureNode> getTransitions() {
        return myTransitions;
    }
}
