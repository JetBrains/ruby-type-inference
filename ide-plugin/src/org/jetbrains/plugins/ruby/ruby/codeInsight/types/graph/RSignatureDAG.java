package org.jetbrains.plugins.ruby.ruby.codeInsight.types.graph;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.ClassModuleSymbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.CoreTypes;
import org.jetbrains.ruby.codeInsight.types.signature.RSignature;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureBuilder;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RSignatureDAG {
    @NotNull
    private static final Map<Couple<List<String>>, Integer> ourArgsTypeNameDistancesCache = new HashMap<>();

    @NotNull
    private final Project myProject;
    @NotNull
    private final Node<RSignature> myRoot;

    @Nullable
    public static Integer getArgsTypeNamesDistance(@NotNull final Project project,
                                                   @NotNull final List<String> from, @NotNull final List<String> to) {
        final Couple<List<String>> key = Couple.of(from, to);
        if (!ourArgsTypeNameDistancesCache.containsKey(key)) {
            ourArgsTypeNameDistancesCache.put(key, calcArgsTypeNamesDistance(project, from, to));
        }

        return ourArgsTypeNameDistancesCache.get(key);
    }

    @Nullable
    public static ClassModuleSymbol getLeastCommonSuperclass(@NotNull final Set<ClassModuleSymbol> returnTypeSymbols) {
        final List<ClassModuleSymbol> longestCommonPrefix = returnTypeSymbols.stream()
                .filter(Objects::nonNull)
                .map(RSignatureDAG::getInheritanceHierarchy)
                .reduce(RSignatureDAG::getLongestCommonPrefix)
                .orElse(null);
        if (longestCommonPrefix != null && !longestCommonPrefix.isEmpty()) {
            return longestCommonPrefix.get(longestCommonPrefix.size() - 1);
        }

        return null;
    }

    public RSignatureDAG(@NotNull final Project project, final int dimension) {
        this.myProject = project;
        final RSignature rootSignature = new RSignatureBuilder("*")
                .setArgsTypeName(new ArrayList<>(Collections.nCopies(dimension, CoreTypes.Object)))
                .build();
        this.myRoot = new Node<>(rootSignature);
    }

    public void add(@NotNull final RSignature signature) {
        final Node<RSignature> newNode = new Node<>(signature);
        add(myRoot, newNode);
    }

    public void addAll(@NotNull final List<RSignature> signatures) {
        signatures.stream()
                .map(signature -> {
                    final Integer distance = getArgsTypeNamesDistance(myProject, signature.getArgsTypes(),
                            myRoot.getVertex().getArgsTypes());
                    return Pair.create(signature, distance);
                })
                .sorted((p1, p2) -> p1.getSecond().compareTo(p2.getSecond()))
                .map(pair -> pair.getFirst())
                .forEach(this::add);
    }

    public void depthFirstSearch(@NotNull final Consumer<RSignature> visitor) {
        depthFirstSearch(myRoot, visitor, new HashSet<>());
    }

    @NotNull
    public List<RSignature> findClosest(@NotNull final RSignature signature) {
        final List<Pair<Node<RSignature>, Integer>> nodesAndDistances = findClosest(signature, myRoot, 0);
        final int minDistance = nodesAndDistances.stream()
                .mapToInt(p -> p.second)
                .min()
                .orElse(0);
        return nodesAndDistances.stream()
                .filter(p -> p.second == minDistance)
                .map(p -> p.first)
                .map(Node::getVertex)
                .collect(Collectors.toList());
    }

    public void removeAll() {
        myRoot.getEdges().forEach(myRoot::removeEdge);
    }

    public List<RSignature> toList() {
        final List<RSignature> result = new ArrayList<>();
        depthFirstSearch(result::add);
        return result;
    }

    @Nullable
    private static Integer calcArgsTypeNamesDistance(@NotNull final Project project,
                                                     @NotNull final List<String> from, @NotNull final List<String> to) {
        if (from.size() != to.size()) {
            return null;
        }

        Integer distanceBetweenAllArgs = 0;
        for (int i = 0; i < from.size(); i++) {
            final String fromArgTypeName = from.get(i);
            final String toArgTypeName = to.get(i);
            if (!fromArgTypeName.equals(toArgTypeName)) {
                Symbol fromArgSymbol = SymbolUtil.findClassOrModule(project, fromArgTypeName);
                Symbol toArgSymbol = SymbolUtil.findClassOrModule(project, toArgTypeName);
                if (fromArgSymbol == null && toArgTypeName.equals(CoreTypes.Object)) {
                    distanceBetweenAllArgs += 1000;
                } else if (fromArgSymbol != null && toArgSymbol != null) {
                    final Integer distanceBetweenTwoArgs = calcArgTypeSymbolsDistance((ClassModuleSymbol) fromArgSymbol,
                                                                                      (ClassModuleSymbol) toArgSymbol);
                    if (distanceBetweenTwoArgs == null) {
                        return null;
                    } else {
                        distanceBetweenAllArgs += distanceBetweenTwoArgs;
                    }
                } else {
                    return null;
                }
            }
        }

        return distanceBetweenAllArgs;
    }

    @Nullable
    private static Integer calcArgTypeSymbolsDistance(@NotNull final ClassModuleSymbol from,
                                                      @NotNull final ClassModuleSymbol to) {
        int distance = 0;
        for (ClassModuleSymbol current = from; current != null; current = (ClassModuleSymbol) current.getSuperClassSymbol(null)) {
            if (current.equals(to)) {
                return distance;
            }

            distance += 1;
        }

        if (to.getName() != null && to.getName().equals(CoreTypes.Object)) {
            return distance;
        }

        return null;
    }

    @NotNull
    private static List<ClassModuleSymbol> getInheritanceHierarchy(@NotNull final ClassModuleSymbol classSymbol) {
        final List<ClassModuleSymbol> inheritanceHierarchy = new ArrayList<>();
        for (ClassModuleSymbol currentClassSymbol = classSymbol;
             currentClassSymbol != null;
             currentClassSymbol = (ClassModuleSymbol) currentClassSymbol.getSuperClassSymbol(null)) {
            inheritanceHierarchy.add(currentClassSymbol);
        }

        Collections.reverse(inheritanceHierarchy);
        return inheritanceHierarchy;
    }

    @NotNull
    private static <T> List<T> getLongestCommonPrefix(@NotNull final List<T> list1, @NotNull final List<T> list2) {
        final int minSize = Math.min(list1.size(), list2.size());
        int prefixLength;
        for (prefixLength = 0; prefixLength < minSize; prefixLength++) {
            if (!list1.get(prefixLength).equals(list2.get(prefixLength))) {
                break;
            }
        }

        return list1.subList(0, prefixLength);
    }

    private void add(@NotNull final Node<RSignature> currentNode, @NotNull final Node<RSignature> newNode) {
        final List<Edge<RSignature>> edges = currentNode.getEdges().stream()
                .filter(edge -> {
                    final List<String> argsTypeNameFrom = newNode.getVertex().getArgsTypes();
                    final List<String> argsTypeNameTo = edge.getTo().getVertex().getArgsTypes();
                    return getArgsTypeNamesDistance(myProject, argsTypeNameFrom, argsTypeNameTo) != null;
                })
                .collect(Collectors.toList());
        if (!edges.isEmpty()) {
            edges.forEach(edge -> add(edge.getTo(), newNode));
            return;
        }

        if (newNode.getVertex().getReturnTypeName().equals(currentNode.getVertex().getReturnTypeName())) {
            return;
        }

        if (tryToMergeChildrenWithNode(currentNode, newNode)) {
            return;
        }

        final List<String> argsTypeNameFrom = newNode.getVertex().getArgsTypes();
        final List<String> argsTypeNameTo = currentNode.getVertex().getArgsTypes();
        @SuppressWarnings("ConstantConditions")
        final Integer weight = getArgsTypeNamesDistance(myProject, argsTypeNameFrom, argsTypeNameTo);
        currentNode.addEdgeTo(newNode, weight);
    }

    private boolean tryToMergeChildrenWithNode(final @NotNull Node<RSignature> currentNode,
                                               final @NotNull Node<RSignature> newNode) {
        final List<Edge<RSignature>> edges = currentNode.getEdges().stream()
                .filter(edge -> {
                    final String returnTypeNameFrom = newNode.getVertex().getReturnTypeName();
                    final String returnTypeNameTo = edge.getTo().getVertex().getReturnTypeName();
                    return returnTypeNameFrom.equals(returnTypeNameTo);
                })
                .collect(Collectors.toList());
        for (final Edge<RSignature> edge : edges) {
            final Node<RSignature> childNode = edge.getTo();
            final RSignature mergedSignature = mergeSignatures(newNode.getVertex(), childNode.getVertex());
            if (!mergedSignature.getArgsTypes().equals(currentNode.getVertex().getArgsTypes()) &&
                !mergedSignature.getReturnTypeName().equals(currentNode.getVertex().getReturnTypeName())) {
                final Node<RSignature> mergedNode = new Node<>(mergedSignature);
                copyChildren(childNode, mergedNode);
                final List<String> currentArgsTypeName = currentNode.getVertex().getArgsTypes();
                final List<String> mergedArgsTypeName = mergedNode.getVertex().getArgsTypes();
                @SuppressWarnings("ConstantConditions")
                final Integer weight = getArgsTypeNamesDistance(myProject, mergedArgsTypeName, currentArgsTypeName);
                currentNode.removeEdgeTo(childNode);
                currentNode.addEdgeTo(mergedNode, weight);
                return true;
            }
        }

        return false;
    }

    private void copyChildren(@NotNull final Node<RSignature> from, @NotNull final Node<RSignature> to) {
        for (final Edge<RSignature> edge : from.getEdges()) {
            final Node<RSignature> childNode = edge.getTo();
            final List<String> fromArgsTypeName = childNode.getVertex().getArgsTypes();
            final List<String> toArgsTypeName = to.getVertex().getArgsTypes();
            @SuppressWarnings("ConstantConditions")
            final int weight = getArgsTypeNamesDistance(myProject, fromArgsTypeName, toArgsTypeName);
            to.addEdgeTo(childNode, weight);
        }
    }

    private RSignature mergeSignatures(@NotNull final RSignature signature1, @NotNull final RSignature signature2) {
        final List<String> argsTypeName1 = signature1.getArgsTypes();
        final List<String> argsTypeName2 = signature2.getArgsTypes();
        final List<String> mergedArgsTypeName = new ArrayList<>();
        for (int i = 0; i < argsTypeName1.size(); i++) {
            final ClassModuleSymbol argClass1 = (ClassModuleSymbol) SymbolUtil.findClassOrModule(myProject, argsTypeName1.get(i));
            final ClassModuleSymbol argClass2 = (ClassModuleSymbol) SymbolUtil.findClassOrModule(myProject, argsTypeName2.get(i));
            final Set<ClassModuleSymbol> set = ContainerUtilRt.newHashSet(argClass1, argClass2);
            final ClassModuleSymbol leastCommonSuperclass = getLeastCommonSuperclass(set);
            mergedArgsTypeName.add(leastCommonSuperclass != null ? leastCommonSuperclass.getName() : CoreTypes.Object);
        }

        return new RSignatureBuilder(signature1.getMethodInfo().getMethodName())
                .setReceiverName(signature1.getMethodInfo().getReceiverName())
                .setVisibility(signature1.getMethodInfo().getVisibility())
                .setArgsInfo(signature1.getArgsInfo())
                .setArgsTypeName(mergedArgsTypeName)
                .setGemInfo(signature1.getMethodInfo().getGemInfo())
                .setReturnTypeName(signature1.getReturnTypeName())
                .build();
    }

    private void depthFirstSearch(@NotNull final Node<RSignature> node, @NotNull final Consumer<RSignature> visitor,
                                  @NotNull final Set<RSignature> visited) {
        for (final Edge<RSignature> edge : node.getEdges()) {
            if (visited.add(edge.getTo().getVertex())) {
                visitor.accept(edge.getTo().getVertex());
                depthFirstSearch(edge.getTo(), visitor, visited);
            }
        }
    }

    private List<Pair<Node<RSignature>, Integer>> findClosest(@NotNull final RSignature signature,
                                                              @NotNull final Node<RSignature> currentNode,
                                                              final int distance) {
        final List<Pair<Node<RSignature>, Integer>> signaturesAndDistances = currentNode.getEdges().stream()
                .map(Edge::getTo)
                .map(v -> Pair.create(v, RSignatureDAG.getArgsTypeNamesDistance(myProject,
                        signature.getArgsTypes(),
                        v.getVertex().getArgsTypes())))
                .filter(p -> p.second != null)
                .collect(Collectors.toList());

        if (signaturesAndDistances.isEmpty()) {
            return new ArrayList<Pair<Node<RSignature>, Integer>>() {{
                add(Pair.create(currentNode, distance));
            }};
        }

        return signaturesAndDistances.stream()
                .flatMap(p -> findClosest(signature, p.first, p.second + distance).stream())
                .collect(Collectors.toList());
    }
}
