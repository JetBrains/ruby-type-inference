package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArgumentToBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;
import org.jetbrains.ruby.codeInsight.types.signature.ParameterInfo;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContractNode;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureFetcher;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;

import java.util.*;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {

    private int countMask(List<Set<String>> readTypes, String type, int currPosition) {
        int ans = 0;

        for (int i = currPosition - 1; i >= 0; i--) {
            ans <<= 1;

            if (readTypes.get(i).contains(type)) {
                ans |= 1;
            }
        }

        return ans;
    }

    private Map<RSignatureContractNode, List<Set<String>>> getNextLevel(Map<RSignatureContractNode, List<Set<String>>> currNodesAndReadTypes, Set<String> argTypeNames, int pos) {

        Map<RSignatureContractNode, List<Set<String>>> nextLayer = new HashMap<>();
        currNodesAndReadTypes.forEach((node, readTypeSets) -> {

            for (final String typeName : argTypeNames) {
                TypedContractTransition transition = new TypedContractTransition(typeName);

                if (node.containsKey(transition)) {
                    final List<Set<String>> newList = new ArrayList<>(readTypeSets);
                    newList.add(ContainerUtil.newHashSet(typeName));

                    addReadTypesList(nextLayer, newList, node.goByTransition(transition));
                } else {
                    int mask = countMask(readTypeSets, typeName, pos);

                    ReferenceContractTransition transition1 = new ReferenceContractTransition(mask);

                    if (node.containsKey(transition1)) {
                        final List<Set<String>> newList = new ArrayList<>(readTypeSets);
                        newList.add(ContainerUtil.newHashSet(typeName));

                        addReadTypesList(nextLayer, newList, node.goByTransition(transition1));
                    }
                }
            }

        });

        return nextLayer;
    }

    private Map<RSignatureContractNode, List<Set<String>>> getNextLevelByString(Map<RSignatureContractNode, List<Set<String>>> currNodesAndReadTypes, String argName, int pos) {

        Set<String> tmpSet = new HashSet<>();
        tmpSet.add(argName);

        return getNextLevel(currNodesAndReadTypes, tmpSet, pos);
    }

    public Symbol findMethodForType(@NotNull RType var1, @NotNull String var2) {
        return null;
    }

    public RType createTypeByCallAndArgs(@NotNull final RExpression call, @NotNull final List<RPsiElement> callArgs) {

        final PsiElement callElement = call instanceof RCall ? ((RCall) call).getPsiCommand() : call;

        SignatureServer callStatServer = SignatureServer.getInstance();

        final Couple<String> names = getMethodAndReceiverNames(callElement);

        final String methodName = names.getFirst();

        HashMap<String, RPsiElement> kwArgs = new HashMap<>();
        LinkedList<RPsiElement> simpleArgs = new LinkedList<>();

        for (RPsiElement arg : callArgs) {
            if (arg instanceof RAssoc)
                kwArgs.put(((RAssoc) arg).getKeyText(), arg);
            else
                simpleArgs.add(arg);
        }

        RSignatureFetcher fetcher = new RSignatureFetcher(callArgs.size(), kwArgs.keySet());

        if (methodName != null) {

            final Module module = ModuleUtilCore.findModuleForPsiElement(call);
            final String receiverName = StringUtil.notNullize(names.getSecond(), CoreTypes.Object);

            RSignatureContract contract = callStatServer.getContractByMethodAndReceiverName(methodName, receiverName);

            if (contract == null)
                return REmptyType.INSTANCE;

            Map<RSignatureContractNode, List<Set<String>>> currNodesAndReadTypes = new HashMap<>();
            currNodesAndReadTypes.put(contract.getStartNode(), new ArrayList<>());

            if (contract != null && contract.locked == false) {
                List<ParameterInfo> paramInfos = contract.getParamInfoList();
                List<Boolean> flags = fetcher.fetch(paramInfos);

                for (int i = 0; i < flags.size(); i++) {
                    if (flags.get(i)) {
                        if (paramInfos.get(i).getModifier() == ParameterInfo.Type.KEY ||
                                paramInfos.get(i).getModifier() == ParameterInfo.Type.KEYREQ) {
                            RPsiElement currElement = kwArgs.get(paramInfos.get(i).getName());

                            currNodesAndReadTypes = getNextLevel(currNodesAndReadTypes, getArgTypeNames(currElement), i);
                        } else {
                            RPsiElement currElement = simpleArgs.poll();
                            currNodesAndReadTypes = getNextLevel(currNodesAndReadTypes, getArgTypeNames(currElement), i);
                        }
                    } else {
                        currNodesAndReadTypes = getNextLevelByString(currNodesAndReadTypes, "-", i);
                    }
                }
            }

            final Set<String> returnTypes = new HashSet<>();
            currNodesAndReadTypes.forEach((node, readTypeSets) -> {
                for (final ContractTransition transition : node.getTransitionKeys()) {
                    returnTypes.addAll(transition.getValue(readTypeSets));
                }
            });

            return returnTypes.stream()
                    .map(name -> RTypeFactory.createTypeByFQN(call.getProject(), name))
                    .reduce(REmptyType.INSTANCE, RTypeUtil::union);

        }

        return REmptyType.INSTANCE;
    }

    private void addReadTypesList(Map<RSignatureContractNode, List<Set<String>>> nextLayer, List<Set<String>> readTypeSets, RSignatureContractNode to) {
        nextLayer.computeIfPresent(to, (rSignatureContractNode, sets) -> {
            for (int i = 0; i < sets.size(); i++) {
                sets.get(i).addAll(readTypeSets.get(i));
            }
            return sets;
        });
        nextLayer.putIfAbsent(to, readTypeSets);
    }


    @NotNull
    private static Couple<String> getMethodAndReceiverNames(@NotNull final PsiElement methodElement) {
        String methodName = null;
        String receiverFQN = null;

        final Symbol methodSymbol = ResolveUtil.resolveToSymbolWithCaching(methodElement.getReference(), false);
        if (methodSymbol != null) {
            final FQN methodFQN = methodSymbol.getFQNWithNesting();
            methodName = methodFQN.getShortName();
            receiverFQN = methodFQN.getCallerFQN().getFullPath();
            return new Couple<>(methodName, receiverFQN);
        } else if (methodElement instanceof RPossibleCall) {
            methodName = ((RPossibleCall) methodElement).getName();
            if (methodElement instanceof RReference) {
                final RPsiElement receiver = ((RReference) methodElement).getReceiver();
                if (receiver instanceof RExpression) {
                    receiverFQN = ((RExpression) receiver).getType().getName();
                }
            } else {
                final Symbol receiverSymbol = SymbolUtil.getScopeContext(methodElement);
                if (receiverSymbol != null && SymbolUtil.isClassOrModuleSymbol(receiverSymbol.getType())) {
                    receiverFQN = receiverSymbol.getFQNWithNesting().getFullPath();
                }
            }
        }

        return new Couple<>(methodName, receiverFQN);
    }


    @NotNull
    private static Set<String> getArgTypeNames(@NotNull RPsiElement arg) {
        if (arg instanceof RAssoc) {
            arg = ((RAssoc) arg).getValue();
        }

        final Set<String> argTypeNames = new HashSet<>();
        if (arg instanceof RExpression) {
            RType type = ((RExpression) arg).getType();
            if (type instanceof RUnionType) {
                final Deque<RType> types = new ArrayDeque<>();
                types.add(type);
                while (!types.isEmpty()) {
                    type = types.pop();
                    if (type instanceof RUnionType) {
                        types.add(((RUnionType) type).getType1());
                        types.add(((RUnionType) type).getType2());
                    } else {
                        argTypeNames.add(StringUtil.notNullize(type.getName(), CoreTypes.Object));
                    }
                }
            } else {
                argTypeNames.add(StringUtil.notNullize(type.getName(), CoreTypes.Object));
            }
        } else if (arg instanceof RArgumentToBlock) {
            argTypeNames.add(CoreTypes.Proc);
        }

        return argTypeNames;
    }
}
