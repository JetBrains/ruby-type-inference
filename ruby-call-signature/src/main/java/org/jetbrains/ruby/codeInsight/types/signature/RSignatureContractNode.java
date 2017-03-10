package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RSignatureContractNode implements SignatureNode {

    private boolean hasReferenceLinks = false;

    private int mask = 0;

    @NotNull
    private final Map<ContractTransition, SignatureNode> typeTransitions;

    public RSignatureContractNode(@NotNull ContractNodeType type) {

        if (type != ContractNodeType.returnTypeNode) {
            typeTransitions = new HashMap<>();
        } else {
            typeTransitions = Collections.emptyMap();
        }
    }

    void setMask(int mask) {
        this.mask = mask;
    }

    void changeTransitionType(ContractTransition oldTransition, ContractTransition newTransition) {
        SignatureNode node = typeTransitions.get(oldTransition);
        typeTransitions.remove(oldTransition);
        typeTransitions.put(newTransition, node);
    }

    public RSignatureContractNode goByTypeSymbol(ContractTransition typeName) {
        return ((RSignatureContractNode) typeTransitions.get(typeName));
    }

    void setReferenceLinks() {
        hasReferenceLinks = true;
    }

    public boolean getReferenceLinksFlag() {
        return hasReferenceLinks;
    }

    int getMask() {
        return this.mask;
    }

    void updateMask(int tempMask) {
        mask &= tempMask;
    }

    public Set<ContractTransition> getTransitionKeys() {
        return this.typeTransitions.keySet();
    }

    public boolean containsKey(ContractTransition key) {
        return this.typeTransitions.containsKey(key);
    }

    public void addLink(final ContractTransition transition, RSignatureContractNode arrivalNode) {
        this.typeTransitions.put(transition, arrivalNode);
    }

    @NotNull
    @Override
    public Map<ContractTransition, SignatureNode> getTransitions() {
        return typeTransitions;
    }

    public enum ContractNodeType {
        argNode,
        returnNode,
        returnTypeNode
    }
}
