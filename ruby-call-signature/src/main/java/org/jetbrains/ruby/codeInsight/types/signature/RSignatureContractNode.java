package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.ruby.codeInsight.types.signature.ContractTransition.ContractTransition;

import java.util.HashMap;
import java.util.Set;

public class RSignatureContractNode {

    private boolean hasReferenceLinks = false;
    private ContractNodeType nodeType;

    public void setMask(int mask) {
        this.mask = mask;
    }

    public void changeTransitionType(ContractTransition oldTransition, ContractTransition newTransition) {
        RSignatureContractNode node = typeTransitions.get(oldTransition);
        typeTransitions.remove(oldTransition);
        typeTransitions.put(newTransition, node);
    }

    public enum ContractNodeType {
        argNode,
        returnNode,
        returnTypeNode
    }

    private int mask = 0;

    private HashMap<ContractTransition, RSignatureContractNode> typeTransitions;

    public RSignatureContractNode goByTypeSymbol(ContractTransition typeName)
    {
        return typeTransitions.get(typeName);
    }

    public void setReferenceLinks() {
        hasReferenceLinks = true;
    }

    public boolean getReferenceLinksFlag() {
        return hasReferenceLinks;
    }

    public RSignatureContractNode(ContractNodeType type) {

        this.nodeType = type;

        if (type != ContractNodeType.returnTypeNode)
            this.typeTransitions = new HashMap<>();
    }

    public ContractNodeType getNodeType()
    {
        return this.nodeType;
    }

    public int getMask() {
        return this.mask;
    }

    public void updateMask(int tempMask) {
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

    public HashMap<ContractTransition, RSignatureContractNode> getTypeTransitions() {
        return typeTransitions;
    }
}