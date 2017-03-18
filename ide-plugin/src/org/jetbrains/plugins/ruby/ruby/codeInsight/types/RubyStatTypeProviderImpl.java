package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Range;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.paramHints.RubyParameterHintsProvider;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSyntheticSymbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArgumentToBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo;
import org.jetbrains.ruby.codeInsight.types.signature.ParameterInfo;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContractNode;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;

import java.util.*;
import java.util.stream.Collectors;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {

    private Map<RSignatureContractNode, List<Set<String>>> getNextLevel(Map<RSignatureContractNode, List<Set<String>>> currNodesAndReadTypes, Set<String> argTypeNames) {

        //final Set<String> argTypeNames = getArgTypeNames(argument);

        Map<RSignatureContractNode, List<Set<String>>> nextLayer = new HashMap<>();
        currNodesAndReadTypes.forEach((node, readTypeSets) -> {
            if (node.getReferenceLinksFlag()) {
                ContractTransition transition = node.getTransitionKeys().iterator().next();

                if (transition.getValue(readTypeSets).containsAll(argTypeNames)) {
                    readTypeSets.add(argTypeNames);

                    final RSignatureContractNode to = node.goByTypeSymbol(transition);
                    addReadTypesList(nextLayer, readTypeSets, to);
                }
            } else {
                for (final String typeName : argTypeNames) {
                    TypedContractTransition transition = new TypedContractTransition(typeName);

                    if (node.containsKey(transition)) {
                        final List<Set<String>> newList = new ArrayList<>(readTypeSets);
                        newList.add(ContainerUtil.newHashSet(typeName));

                        addReadTypesList(nextLayer, newList, node.goByTypeSymbol(transition));
                    }
                }
            }


        });

        return nextLayer;
    }

    private Map<RSignatureContractNode, List<Set<String>>> getNextLevelByString(Map<RSignatureContractNode, List<Set<String>>> currNodesAndReadTypes, String argName) {

        Set<String> tmpSet = new HashSet<>();
        tmpSet.add(argName);

        return getNextLevel(currNodesAndReadTypes, tmpSet);
    }

    @Override
    @Nullable
    public Symbol findMethodForType(@NotNull RType type, @NotNull String name) {
        if (!(type instanceof RSymbolType)) {
            return null;
        }
        final String typeName = type.getName();
        if (typeName == null) {
            return null;
        }
        SignatureServer callStatServer = SignatureServer.getInstance();
        final MethodInfo methodInfo = callStatServer.getMethodByClass(typeName, name);
        if (methodInfo == null) {
            return null;
        }
        final RSignatureContract contract = callStatServer.getContract(methodInfo);
        if (contract == null) {
            return null;
        }
        return new RMethodSyntheticSymbol(((RSymbolType) type).getSymbol().getProject(),
                methodInfo,
                Type.INSTANCE_METHOD,
                ((RSymbolType) type).getSymbol(),
                contract.getArgsInfo().stream().map(RubyStatTypeProviderImpl::toArgInfo).collect(Collectors.toList())
        );
    }

    @Override
    @NotNull
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

        if (methodName != null) {

            final Module module = ModuleUtilCore.findModuleForPsiElement(call);
            final String receiverName = StringUtil.notNullize(names.getSecond(), CoreTypes.Object);

            RSignatureContract contract = callStatServer.getContractByMethodName(methodName);
            // TODO fix npe (done)

            if (contract == null)
                return REmptyType.INSTANCE;

            Map<RSignatureContractNode, List<Set<String>>> currNodesAndReadTypes = new HashMap<>();
            currNodesAndReadTypes.put(contract.getStartNode(), new ArrayList<>());

            final List<ParameterInfo> paramInfos = contract.getParamInfoList();
            final Map<ArgumentInfo, Range<Integer>> mapping = RubyParameterHintsProvider.Companion.getArgsMapping(paramInfos.stream().map(RubyStatTypeProviderImpl::toArgInfo).collect(Collectors.toList()), callArgs);
            for (final ParameterInfo paramInfo : paramInfos) {
                final Set<String> type;
                final Range<Integer> range = mapping.get(toArgInfo(paramInfo));
                if (paramInfo.getModifier() == ParameterInfo.Type.REST) {
                    type = Collections.singleton(range != null && range.getFrom() <= range.getTo() ? "Array" : "EmptyArray");
                } else if (range == null) {
                    type = Collections.singleton("-");
                } else {
                    type = getArgTypeNames(callArgs.get(range.getFrom()));
                }
                currNodesAndReadTypes = getNextLevel(currNodesAndReadTypes, type);
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

    private static ArgumentInfo toArgInfo(@NotNull ParameterInfo info) {
        ArgumentInfo.Type type = null;
        switch (info.getModifier()) {

            case REQ:
                type = ArgumentInfo.Type.NAMED;
                break;
            case OPT:
                type = ArgumentInfo.Type.PREDEFINED;
                break;
            case REST:
                type = ArgumentInfo.Type.ARRAY;
                break;
            case KEYREQ:
                type = ArgumentInfo.Type.NAMED;
                break;
            case KEY:
                type = ArgumentInfo.Type.NAMED;
                break;
            case KEYREST:
                type = ArgumentInfo.Type.HASH;
                break;
            case BLOCK:
                type = ArgumentInfo.Type.BLOCK;
                break;
        }
        return new ArgumentInfo(StringRef.fromString(info.getName()), type);
    }
}
