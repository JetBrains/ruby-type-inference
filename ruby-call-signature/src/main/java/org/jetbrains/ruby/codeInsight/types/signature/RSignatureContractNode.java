package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RSignatureContractNode implements SignatureNode {

    private boolean hasReferenceLinks = false;

    @NotNull
    private final Map<ContractTransition, SignatureNode> transitions;

    public RSignatureContractNode() {

        transitions = new HashMap<>();
    }

    @NotNull
    public RSignatureContractNode goByTransition(ContractTransition transition) {

        return ((RSignatureContractNode) transitions.get(transition));
    }

    public boolean getReferenceLinksFlag() {
        return hasReferenceLinks;
    }

    @NotNull
    public Set<ContractTransition> getTransitionKeys() {
        return this.transitions.keySet();
    }

    public void addLink(final @NotNull ContractTransition transition, @NotNull RSignatureContractNode arrivalNode) {
        this.transitions.put(transition, arrivalNode);
    }

    public boolean containsKey(final @NotNull ContractTransition transition) {
        return this.transitions.keySet().contains(transition);
    }


    @NotNull
    @Override
    public Map<ContractTransition, SignatureNode> getTransitions() {
        return transitions;
    }

    public enum ContractNodeType {
        argNode,
        returnNode,
        returnTypeNode
    }
}
