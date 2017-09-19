package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.util.GemSearchUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSyntheticSymbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.holders.RContainer;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArgumentToBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureProvider;
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;
import org.jetbrains.ruby.runtime.signature.server.serialisation.RTupleBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {

    private int countMask(List<Set<String>> readTypes, String type, int currPosition) {
        int ans = 0;

        List<Set<String>> readTypesReversed = ContainerUtil.reverse(readTypes.subList(0, currPosition));

        for (Set<String> strings : readTypesReversed) {
            ans <<= 1;

            if (strings.contains(type)) {
                ans |= 1;
            }
        }

        return ans;
    }

    private Map<SignatureNode, List<Set<String>>> getNextLevel(Map<SignatureNode, List<Set<String>>> currNodesAndReadTypes, Set<String> argTypeNames, int pos) {

        Map<SignatureNode, List<Set<String>>> nextLayer = new HashMap<>();
        currNodesAndReadTypes.forEach((node, readTypeSets) -> {

            for (final String typeName : argTypeNames) {
                TypedContractTransition typedTransition = new TypedContractTransition(typeName);

                if (node.getTransitions().containsKey(typedTransition)) {
                    final List<Set<String>> newList = new ArrayList<>(readTypeSets);
                    newList.add(ContainerUtil.newHashSet(typeName));

                    addReadTypesList(nextLayer, newList, node.getTransitions().get(typedTransition));
                } else {
                    int mask = countMask(readTypeSets, typeName, pos);

                    ReferenceContractTransition refTransition = new ReferenceContractTransition(mask);

                    if (node.getTransitions().containsKey(refTransition)) {
                        final List<Set<String>> newList = new ArrayList<>(readTypeSets);
                        newList.add(ContainerUtil.newHashSet(typeName));

                        addReadTypesList(nextLayer, newList, node.getTransitions().get(refTransition));
                    }
                }
            }

        });

        return nextLayer;
    }

    private Map<SignatureNode, List<Set<String>>> getNextLevelByString(Map<SignatureNode, List<Set<String>>> currNodesAndReadTypes, String argName, int pos) {

        Set<String> tmpSet = new HashSet<>();
        tmpSet.add(argName);

        return getNextLevel(currNodesAndReadTypes, tmpSet, pos);
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
        final Module module = ((RSymbolType) type).getSymbol().getModule();
        if (module == null) {
            return null;
        }

        SignatureServer callStatServer = SignatureServer.INSTANCE;
        final MethodInfo method = findMethodInfo(name, typeName, module, callStatServer.getStorage());

        if (method == null) {
            return null;
        }
        final SignatureContract contract = callStatServer.getContract(method);
        if (contract == null) {
            return null;
        }
        return new RMethodSyntheticSymbol(((RSymbolType) type).getSymbol().getProject(),
                method,
                Type.INSTANCE_METHOD,
                ((RSymbolType) type).getSymbol(),
                contract.getArgsInfo().stream().map(RubyStatTypeProviderImpl::toArgInfo).collect(Collectors.toList())
        );
    }

    @Nullable
    public static MethodInfo findMethodInfo(@NotNull String name,
                                            @NotNull String typeName,
                                            @NotNull Module module,
                                            @NotNull RSignatureProvider signatureProvider) {
        try {
            return signatureProvider.getAllClassesWithFQN(typeName).stream()
                    .filter(classInfo -> {
                        final GemInfo gemInfo = classInfo.getGemInfo();
                        if (gemInfo == null || "LOCAL".equals(gemInfo.getName())) {
                            return true;
                        }
                        final String parentGem = gemInfo.getName();
                        return GemSearchUtil.findGem(module, parentGem) != null;
                    })
                    .map(classInfo -> {
                        try {
                            return signatureProvider.getRegisteredMethods(classInfo).stream()
                                    .filter(candidate -> name.equals(candidate.getName()))
                                    .findAny();
                        } catch (StorageException e) {
                            return Optional.<MethodInfo>empty();
                        }
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findAny()
                    .orElse(null);
        } catch (StorageException e) {
            return null;
        }
    }

    @NotNull
    public RType createTypeByCallAndArgs(@NotNull final RExpression call, @NotNull final List<RPsiElement> callArgs) {

        final PsiElement callElement = call instanceof RCall ? ((RCall) call).getPsiCommand() : call;

        SignatureServer callStatServer = SignatureServer.INSTANCE;

        final MethodInfo methodInfo = findMethodInfo(callElement);
        if (methodInfo == null) {
            return REmptyType.INSTANCE;
        }
        SignatureContract contract = callStatServer.getContract(methodInfo);

        if (contract == null)
            return REmptyType.INSTANCE;

        HashMap<String, RPsiElement> kwArgs = new HashMap<>();
        LinkedList<RPsiElement> simpleArgs = new LinkedList<>();

        List<ParameterInfo> paramInfos = contract.getArgsInfo();
        for (int i1 = 0; i1 < callArgs.size(); i1++) {
            final RPsiElement arg = callArgs.get(i1);
            if (arg instanceof RAssoc)
                kwArgs.put(((RAssoc) arg).getKeyText(), arg);
            else
                simpleArgs.add(arg);

            if ((arg instanceof RAssoc) ^ (paramInfos.get(i1).getModifier() == ParameterInfo.Type.KEY ||
                    paramInfos.get(i1).getModifier() == ParameterInfo.Type.KEYREQ)) {
                Logger.getInstance(RubyStatTypeProviderImpl.class).error("wtf");
            }
        }


            Map<SignatureNode, List<Set<String>>> currNodesAndReadTypes = new HashMap<>();
            currNodesAndReadTypes.put(contract.getStartNode(), new ArrayList<>());

            boolean[] isArgumentPresent = RTupleBuilder.calcPresentArguments(paramInfos, callArgs.size(), kwArgs.keySet());

            for (int i = 0; i < isArgumentPresent.length; i++) {
                if (isArgumentPresent[i]) {
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

            final Set<String> returnTypes = new HashSet<>();
            currNodesAndReadTypes.forEach((node, readTypeSets) -> {
                for (final ContractTransition transition : node.getTransitions().keySet()) {
                    returnTypes.addAll(transition.getValue(readTypeSets));
                }
            });

            return returnTypes.stream()
                    .map(name -> RTypeFactory.createTypeByFQN(call.getProject(), name))
                    .reduce(REmptyType.INSTANCE, RTypeUtil::union);

    }

    private void addReadTypesList(Map<SignatureNode, List<Set<String>>> nextLayer, List<Set<String>> readTypeSets, SignatureNode to) {
        nextLayer.computeIfPresent(to, (rSignatureContractNode, sets) -> {
            for (int i = 0; i < sets.size(); i++) {
                sets.get(i).addAll(readTypeSets.get(i));
            }
            return sets;
        });
        nextLayer.putIfAbsent(to, readTypeSets);
    }


    @Nullable
    private static MethodInfo findMethodInfo(@NotNull final PsiElement callElement) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(callElement);
        if (module == null) {
            return null;
        }

        final Symbol methodSymbol = ResolveUtil.resolveToSymbolWithCaching(callElement.getReference(), false);
        if (methodSymbol != null) {
            final FQN methodFQN = methodSymbol.getFQNWithNesting();
            return findMethodInfo(methodFQN.getShortName(),
                    methodFQN.getCallerFQN().getFullPath(),
                    module,
                    SignatureServer.INSTANCE.getStorage());

        } else if (callElement instanceof RPossibleCall) {
            final String methodName = ((RPossibleCall) callElement).getName();
            if (methodName == null) {
                return null;
            }

            String explicitReceiver = "";
            if (callElement instanceof RReference) {
                final RPsiElement receiver = ((RReference) callElement).getReceiver();
                if (receiver instanceof RExpression) {
                    explicitReceiver = StringUtil.notNullize(((RExpression) receiver).getType().getName());
                }
            }

            final RContainer container = RubyPsiUtil.getContainingRClassOrModule(callElement);
            final FQN fqnToProbe;
            if (container == null) {
                fqnToProbe = FQN.Builder.fromString(explicitReceiver);
            } else {
                fqnToProbe = FQN.Builder.concat(container.getFQNWithNesting(), FQN.Builder.fromString(explicitReceiver));
            }

            Ref<MethodInfo> result = Ref.create();
            fqnToProbe.processNestingResolution(fqn -> {
                final String receiver = StringUtil.notNullize(StringUtil.nullize(fqn.getFullPath()), CoreTypes.Object);
                result.set(findMethodInfo(methodName, receiver, module, SignatureServer.INSTANCE.getStorage()));
                return result.isNull();
            });

            if (result.isNull()) {
                final Symbol receiverSymbol = SymbolUtil.getScopeContext(callElement);
                if (receiverSymbol != null && SymbolUtil.isClassOrModuleSymbol(receiverSymbol.getType())) {
                    result.set(findMethodInfo(methodName, receiverSymbol.getFQNWithNesting().getFullPath(), module, SignatureServer.INSTANCE.getStorage()));
                }
            }

            return result.get();
        } else {
            return null;
        }
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
                type = ArgumentInfo.Type.SIMPLE;
                break;
            case OPT:
                type = ArgumentInfo.Type.PREDEFINED;
                break;
            case POST:
                type = ArgumentInfo.Type.SIMPLE;
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
