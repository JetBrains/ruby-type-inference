package org.jetbrains.ruby.codeInsight.types.signature;

import java.util.HashMap;
import java.util.Set;

public class RSignatureContractNode {

    private ContractNodeType nodeType;

    public void setMask(int mask) {
        this.mask = mask;
    }

    public enum ContractNodeType {
        argNode,
        returnNode,
        returnTypeNode
    }

    private int mask = 0;

    private HashMap<String, RSignatureContractNode> typeTransitions;

    public RSignatureContractNode goByTypeSymbol(String typeName)
    {
        return typeTransitions.get(typeName);
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

    public Set<String> getTransitionKeys() {
        return this.typeTransitions.keySet();
    }

    public void addLink(final String type, RSignatureContractNode arrivalNode) {
        this.typeTransitions.put(type, arrivalNode);
    }
}