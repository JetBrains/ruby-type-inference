package org.jetbrains.ruby.runtime.signature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

public class RSignatureContractNode {
    private Integer argIndex;

    private Set<String> nodeTypes = new HashSet<>();
    private String nodeType;

    private String reachableTerm;
    private int mask = 0;

    private HashMap<String, RSignatureContractNode> typeTransitions;

    public RSignatureContractNode goByTypeSymbol(String typeName)
    {
        return typeTransitions.get(typeName);
    }

    public RSignatureContractNode(Integer index) {

        this.argIndex = index;
        this.typeTransitions = new HashMap();
    }
    public String getNodeTypesStringPresentation()
    {
        StringJoiner answer = new StringJoiner("|");
        for (String type : nodeTypes) {
            answer.add(type);
        }
        return answer.toString();
    }

    public String getNodeType()
    {
        return this.nodeType;
    }

    public int getMask() {
        return this.mask;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }
    public void addNodeType(String type)
    {
        if(this.nodeTypes == null)
            this.nodeTypes = new HashSet<>();

        this.nodeTypes.add(type);
    }

    public String getReachableTerm()
    {
        return this.reachableTerm;
    }
    private void setReachableTerm(String reachableTerm)
    {
        this.reachableTerm = reachableTerm;
    }

    public RSignatureContractNode(String type) {
        nodeType = type;
    }

    public Set<String> getTransitionKeys() {
        return this.typeTransitions.keySet();
    }

    public void addLink(final String type, RSignatureContractNode arrivalNode, String returnType) {

        this.typeTransitions.put(type, arrivalNode);

        if(reachableTerm == null)
            reachableTerm = returnType;
        else
        {
            if(!reachableTerm.equals(returnType))
                reachableTerm = null;
        }
    }

    public void updateMask(String type, String returnType) {
        int tmpMask = goByTypeSymbol(type).getMask();
        tmpMask <<= 1;

        if (type.equals(returnType))
            tmpMask |= 1;

        if (typeTransitions.size() == 1)
            setMask(tmpMask);
        else
            setMask(getMask() & tmpMask);
    }
}
