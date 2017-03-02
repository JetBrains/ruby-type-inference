package org.jetbrains.ruby.runtime.signature;

import org.jetbrains.ruby.codeInsight.types.signature.Pair;

import java.util.*;

public class RSignatureContract {

    private int counter = 0;

    private String myMethodName;
    private String myReceiverName;
    private String myGemName;
    private String myGemVersion;
    private List<String> myArgsInfo;
    private RSignatureContractNode startContractNode;

    private List<List<RSignatureContractNode>> levels;
    private Map<String, RSignatureContractNode> termNodes;

    private RSignatureContractNode createNodeAndAddToLevels(Integer index)
    {
        RSignatureContractNode newNode = new RSignatureContractNode(index);
        while(levels.size() <= index)
            levels.add(new ArrayList<>());
        levels.get(index).add(newNode);
        return newNode;
    }

    public int getCounter()
    {
        return counter;
    }

    private RSignatureContractNode createTermNode(String type)
    {
        if(!termNodes.containsKey(type)) {
            RSignatureContractNode newNode = new RSignatureContractNode(type);
            termNodes.put(type, newNode);
        }

        return termNodes.get(type);
    }

    private RSignatureContractNode createJoinTypeTermNode()
    {
        return new RSignatureContractNode("join type node");
    }

    public RSignatureContractNode getTermNode(String type)
    {
        return termNodes.get(type);
    }

    public RSignatureContract(RSignature signature) {
        this.myMethodName = signature.getMethodName();
        this.myReceiverName = signature.getReceiverName();
        this.myGemName = signature.getGemName();
        this.myGemVersion = signature.getGemVersion();

        this.levels = new ArrayList<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        this.addRSignature(signature);
    }

    public RSignatureContract(final String methodName, final String receiverName,
                              final String gemName, final String gemVersion) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName;
        this.myGemName = gemName;
        this.myGemVersion = gemVersion;

        this.levels = new ArrayList<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);
    }
    public RSignatureContract(List<RSignature> signatures) {

        this.levels = new ArrayList<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        for (RSignature signature : signatures) {
            this.addRSignature(signature);
        }
    }

    public RSignatureContractNode getStartNode() {
        return startContractNode;
    }
    public String getMethodName() {
        return myMethodName;
    }

    public String getReceiverName() {
        return myReceiverName;
    }

    public String getGemName() {
        return myGemName;
    }

    public String getGemVersion() {
        return myGemVersion;
    }

    public void addRSignature(RSignature signature) {
        this.counter++;
        RSignatureContractNode currNode = this.startContractNode;
        int i = 1;

        signature.fetch();

        String returnType = signature.getReturnTypeName();
        RSignatureContractNode termNode = getTermNode(returnType);
        if(termNode == null)
        {
            termNode = this.createTermNode(returnType);
        }

        if(signature.getArgsTypeName().size() == 0)
        {
            this.startContractNode.addLink("no arguments", termNode, returnType);
            return;
        }
        Stack<RSignatureContractNode> nodes = new Stack<>();
        nodes.add(currNode);

        for (RMethodArgument argument : signature.getArgsInfo()) {
            String type = argument.getType();
            if(!argument.getIsGiven())
                type = "-";

            if(i == signature.getArgsTypeName().size())
            {
                if(currNode.goByTypeSymbol(type) != null && currNode.goByTypeSymbol(type) != termNode)
                {
                    RSignatureContractNode oldTerm = currNode.goByTypeSymbol(type);
                    if(oldTerm.getNodeType().equals("join type node")){
                        oldTerm.addNodeType(returnType);
                    }
                    else
                    {
                        RSignatureContractNode newTerm = createJoinTypeTermNode();
                        newTerm.addNodeType(oldTerm.getNodeType());
                        newTerm.addNodeType(returnType);
                    }
                }
                else
                    currNode.addLink(type, termNode, returnType);
                break;
            }


            if(currNode.goByTypeSymbol(type) == null)
            {
                RSignatureContractNode newNode = this.createNodeAndAddToLevels(i);
                currNode.addLink(type, newNode, returnType);
                currNode = newNode;
            }
            else
                currNode = currNode.goByTypeSymbol(type);

            i++;
            nodes.add(currNode);
        }

        for (int j = signature.getArgsInfo().size() - 1; j >= 0; j--) {
            RMethodArgument argument = signature.getArgsInfo().get(j);
            String type = argument.getType();
            if (!argument.getIsGiven())
                type = "-";
            currNode = nodes.pop();
            currNode.updateMask(type, returnType);
        }
    }

    public Integer getNumberOfLevels()
    {
        return this.levels.size();
    }

    public void minimization()
    {
        for(int i = this.getNumberOfLevels() - 1; i > 0; i--)
        {
            List<RSignatureContractNode> level = levels.get(i);

            int sizeOfLevel = level.size();
            HashMap <RSignatureContractNode, RSignatureContractNode> representatives = new HashMap<>();
            List<Integer> uselessVertices = new ArrayList<>();

            for (RSignatureContractNode node : level) {
                representatives.put(node, node);
            }

            for(int v1 = 0; v1 < level.size(); v1++){
                for(int v2 = v1 + 1; v2 < level.size(); v2++){
                    RSignatureContractNode vertex1 = level.get(v1);
                    RSignatureContractNode vertex2 = level.get(v2);

                    boolean isSame = true;

                    for (String type : vertex1.getTransitionKeys()) {
                        if(vertex1.goByTypeSymbol(type) != vertex2.goByTypeSymbol(type))
                        {
                            isSame = false;
                        }
                    }

                    for (String type : vertex2.getTransitionKeys()) {
                        if(vertex1.goByTypeSymbol(type) != vertex2.goByTypeSymbol(type))
                        {
                            isSame = false;
                        }
                    }

                    if(isSame)
                    {
                        RSignatureContractNode vertex1presenter = representatives.get(vertex1);
                        representatives.put(vertex2, vertex1presenter);
                        uselessVertices.add(v2);
                    }
                }
            }

            List<RSignatureContractNode> prevLevel = levels.get(i - 1);


            if (uselessVertices.size() > 0) {
                for (RSignatureContractNode node : prevLevel) {
                    for (String type : node.getTransitionKeys()) {
                        RSignatureContractNode child = node.goByTypeSymbol(type);
                        node.addLink(type, representatives.get(child), node.getReachableTerm());
                    }
                }
            }
            for (int index : uselessVertices) {
                if (this.levels.get(i).size() > index)
                    this.levels.get(i).remove(index);
            }
        }
    }

    public List<String> getStringPresentation() {
        List<String> answer = new ArrayList<>();

        Queue<Pair<RSignatureContractNode, String>> tmp = new ArrayDeque<>();
        tmp.add(new Pair<>(this.startContractNode, null));

        while (!tmp.isEmpty())
        {
            RSignatureContractNode currNode = tmp.peek().getFirst();
            String currString = tmp.peek().getSecond();

            tmp.remove();


            if(currNode == null)
            {
                System.out.println("bad");
                // TODO[nick] NPE ahead
            }

            if(currNode.getNodeType() != null)
            {
                if(currNode.getNodeType().equals("join type node"))
                {
                    answer.add("(" + currString + ")" + " -> " + currNode.getNodeTypesStringPresentation());
                    continue;
                }
                else {
                    answer.add("(" + currString + ")" + " -> " + currNode.getNodeType());
                    continue;
                }
            }

            Map <RSignatureContractNode, String> newVertexes = new HashMap<>();

            for (String transitionType : currNode.getTransitionKeys()) {
                RSignatureContractNode tmpNode = currNode.goByTypeSymbol(transitionType);

                if(newVertexes.containsKey(tmpNode))
                {
                    String oldValue = newVertexes.get(tmpNode);
                    newVertexes.remove(tmpNode);
                    newVertexes.put(tmpNode, oldValue + "|" + transitionType);
                }
                else
                {
                    if(transitionType.equals("no arguments"))
                        newVertexes.put(tmpNode, "");
                    else {
                        if (currString == null)
                            newVertexes.put(tmpNode, transitionType);
                        else
                            newVertexes.put(tmpNode, currString + ";" + transitionType);
                    }
                }
            }
            for (RSignatureContractNode node : newVertexes.keySet()) {
                tmp.add(new Pair<>(node, newVertexes.get(node)));
            }
        }

        return answer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignatureContract that = (RSignatureContract) o;

        return myMethodName.equals(that.myMethodName) &&
                myReceiverName.equals(that.myReceiverName) &&
                myGemName.equals(that.myGemName) &&
                myGemVersion.equals(that.myGemVersion);

    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myGemName.hashCode();
        result = 31 * result + myGemVersion.hashCode();
        return result;
    }
}