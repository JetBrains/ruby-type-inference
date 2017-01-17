package signature;

import java.util.HashMap;
import java.util.Set;

public class RSignatureContractNode {
    private Integer argIndex;
    private String nodeType;

    private HashMap<String, RSignatureContractNode> typeTransitions;

    public Integer getIndex() {
        return this.argIndex;
    }

    public RSignatureContractNode goByTypeSymbol(String typeName)
    {
        return typeTransitions.get(typeName);
    }

    public RSignatureContractNode(Integer index) {

        this.argIndex = index;
        this.typeTransitions = new HashMap();
    }

    public String getNodeType()
    {
        return this.nodeType;
    }

    public RSignatureContractNode(String type) {
        this.nodeType = type;
    }

    public Set<String> getTransitionKeys() {
        return this.typeTransitions.keySet();
    }

    public void addLink(final String type, RSignatureContractNode arrivalNode) {
        this.typeTransitions.put(type, arrivalNode);
    }
}
