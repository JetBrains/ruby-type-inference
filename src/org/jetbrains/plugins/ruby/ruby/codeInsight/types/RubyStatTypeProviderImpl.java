package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature.ParameterInfo;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature.RSignature;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature.RSignatureBuilder;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.ProxyCacheRSignatureManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.RSignatureManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.SqliteRSignatureManager;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArgumentToBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArrayToArguments;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;

import java.util.*;
import java.util.stream.Collectors;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {
    @Nullable
    @Override
    public RType createTypeByCallAndArgs(@NotNull final RExpression call, @NotNull final List<RPsiElement> callArgs) {
        final PsiElement callElement = call instanceof RCall ? ((RCall) call).getPsiCommand() : call;
        final Couple<String> names = getMethodAndReceiverNames(callElement);
        final String methodName = names.getFirst();
        if (methodName != null) {
            final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
            if (signatureManager == null) {
                return null;
            }

            final RSignatureManager cacheManager = ProxyCacheRSignatureManager.getInstance(call.getProject(),
                                                                                           signatureManager);
            final Module module = ModuleUtilCore.findModuleForPsiElement(call);
            final String receiverName = StringUtil.notNullize(names.getSecond(), CoreTypes.Object);
            final List<ParameterInfo> argsInfo = cacheManager.getMethodArgsInfo(methodName, receiverName);
            final List<List<String>> argsTypeNames;
            try {
                argsTypeNames = getAllPossibleNormalizedArgsTypeNames(call.getParent(), argsInfo, callArgs);
            } catch (IllegalArgumentException e) {
                return null;
            }

            final List<RType> returnTypes = argsTypeNames.stream()
                    .map(argsTypeName -> new RSignatureBuilder(methodName)
                            .setReceiverName(receiverName)
                            .setArgsInfo(argsInfo)
                            .setArgsTypeName(argsTypeName)
                            .build())
                    .flatMap(signature -> getReturnTypesBySignature(call.getProject(), module, cacheManager, signature).stream())
                    .distinct()
                    .collect(Collectors.toList());

            if (returnTypes.size() == 1) {
                return returnTypes.get(0);
            } else if (returnTypes.size() <= RType.TYPE_TREE_HEIGHT_LIMIT) {
                return returnTypes.stream().reduce(REmptyType.INSTANCE, RTypeUtil::union);
            }
        }

        return null;
    }

    @NotNull
    private static List<RType> getReturnTypesBySignature(@NotNull final Project project, @Nullable final Module module,
                                                         @NotNull final RSignatureManager cacheManager,
                                                         @NotNull final RSignature signature) {
        final List<String> returnTypeNames = cacheManager.findReturnTypeNamesBySignature(project, module, signature);
        return returnTypeNames.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(returnTypeName -> {
                        RType returnType = RTypeFactory.createTypeByFQN(project, returnTypeName);
                        if (returnType == REmptyType.INSTANCE) {
                            returnType = cacheManager.createTypeByFQNFromStat(project, returnTypeName);
                        }

                        return returnType;
                })
                .collect(Collectors.toList());
    }

    @NotNull
    private static Couple<String> getMethodAndReceiverNames(@NotNull final PsiElement methodElement) {
        String methodName = null;
        String receiverFQN = null;

        final Symbol methodSymbol = ResolveUtil.resolveToSymbolWithCaching(methodElement.getReference(), false);
        if (methodSymbol != null) {
            methodName = methodSymbol.getName();
            final String methodFQN = SymbolUtil.getSymbolFullQualifiedName(methodSymbol);
            if (methodFQN != null) {
                final int separatorIndex = methodFQN.lastIndexOf('.');
                receiverFQN = separatorIndex >= 0 ? methodFQN.substring(0, separatorIndex) : null;
                return new Couple<>(methodName, receiverFQN);
            }
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
                    receiverFQN = SymbolUtil.getSymbolFullQualifiedName(receiverSymbol);
                }
            }
        }

        return new Couple<>(methodName, receiverFQN);
    }

    @NotNull
    private static List<List<String>> getAllPossibleNormalizedArgsTypeNames(@NotNull final PsiElement callParent,
                                                                            @NotNull final List<ParameterInfo> argsInfo,
                                                                            @NotNull final List<RPsiElement> args) {
        final Pair<Deque<List<String>>, Map<String, List<String>>> argsAndKwargsTypeNames = getArgsAndKwargsTypeNames(args);
        final Deque<List<String>> argsTypeNames = argsAndKwargsTypeNames.getFirst();
        final Map<String, List<String>> kwargsTypeNames = argsAndKwargsTypeNames.getSecond();
        final Deque<List<String>> normalizedArgsTypeNames = normalizeArgsTypeNames(callParent, argsInfo, argsTypeNames, kwargsTypeNames);
        return constructAllPossibleArgsTypeNamesRecursively(normalizedArgsTypeNames, new ArrayList<>(), new ArrayList<>());
    }

    @NotNull
    private static Pair<Deque<List<String>>, Map<String, List<String>>> getArgsAndKwargsTypeNames(@NotNull final List<RPsiElement> args) {
        final Deque<List<String>> argsTypeNames = new ArrayDeque<>();
        final Map<String, List<String>> kwargsTypeNames = new HashMap<>();

        for (final RPsiElement arg : args) {
            if (arg instanceof RArrayToArguments) {
                continue;
            }

            final List<String> argTypeNames = getArgTypeNames(arg);
            if (arg instanceof RAssoc) {
                kwargsTypeNames.put(((RAssoc) arg).getKeyText(), argTypeNames);
            } else {
                argsTypeNames.add(argTypeNames);
            }
        }

        return Pair.create(argsTypeNames, kwargsTypeNames);
    }

    @NotNull
    private static List<String> getArgTypeNames(@NotNull RPsiElement arg) {
        if (arg instanceof RAssoc) {
            arg = ((RAssoc) arg).getValue();
        }

        final List<String> argTypeNames = new ArrayList<>();
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

    @NotNull
    private static Deque<List<String>> normalizeArgsTypeNames(@NotNull final PsiElement callParent,
                                                              @NotNull final List<ParameterInfo> argsInfo,
                                                              @NotNull final Deque<List<String>> argsTypeNames,
                                                              @NotNull final Map<String, List<String>> kwargsTypeNames) {
        final List<String> possibleBlockType = argsTypeNames.peekLast();
        final boolean hasBlockArg = possibleBlockType != null && possibleBlockType.get(0).equals(CoreTypes.Proc);
        if (hasBlockArg) {
            argsTypeNames.removeLast();
        }

        final Deque<List<String>> normalizedArgsTypeNames = new ArrayDeque<>();
        try {
            for (final ParameterInfo argInfo : argsInfo) {
                switch (argInfo.getType()) {
                    case OPT:
                        if (argsTypeNames.isEmpty()) {
                            final List<String> defaultValueTypeName = new ArrayList<>();
                            defaultValueTypeName.add(argInfo.getDefaultValueTypeName());
                            normalizedArgsTypeNames.addLast(defaultValueTypeName);
                            break;
                        }
                    case REQ:
                        normalizedArgsTypeNames.addLast(argsTypeNames.removeFirst());
                        break;
                    case REST:
                        argsTypeNames.clear();
                        final List<String> restTypeName = new ArrayList<>();
                        restTypeName.add(CoreTypes.Array);
                        normalizedArgsTypeNames.addLast(restTypeName);
                        break;
                    case KEY:
                        if (!kwargsTypeNames.containsKey(argInfo.getName())) {
                            final List<String> defaultValueTypeName = new ArrayList<>();
                            defaultValueTypeName.add(argInfo.getDefaultValueTypeName());
                            normalizedArgsTypeNames.addLast(defaultValueTypeName);
                            break;
                        }
                    case KEYREQ:
                        if (!kwargsTypeNames.containsKey(argInfo.getName())) {
                            throw new NoSuchElementException();
                        }

                        normalizedArgsTypeNames.addLast(kwargsTypeNames.get(argInfo.getName()));
                        break;
                    case KEYREST:
                        final List<String> keyrestTypeName = new ArrayList<>();
                        keyrestTypeName.add(CoreTypes.Hash);
                        normalizedArgsTypeNames.addLast(keyrestTypeName);
                        break;
                    case BLOCK:
                        final List<String> blockTypeName = new ArrayList<>();
                        blockTypeName.add(hasBlockArg || callParent instanceof RBlockCall ? CoreTypes.Proc : CoreTypes.NilClass);
                        normalizedArgsTypeNames.addLast(blockTypeName);
                        break;
                }
            }
        } catch (NoSuchElementException e) {
            throw new IllegalArgumentException(e);
        }

        return normalizedArgsTypeNames;
    }

    private static List<List<String>> constructAllPossibleArgsTypeNamesRecursively(@NotNull final Deque<List<String>> argsTypeNames,
                                                                                   @NotNull final List<String> currentArgsTypeName,
                                                                                   @NotNull final List<List<String>> allPossibleArgsTypeNames) {
        if (argsTypeNames.isEmpty()) {
            allPossibleArgsTypeNames.add(currentArgsTypeName);
        } else {
            final List<String> argTypeNames = argsTypeNames.removeFirst();
            final String argTypeName = argTypeNames.remove(argTypeNames.size() - 1);
            if (!argTypeNames.isEmpty()) {
                for (final String currentArgTypeName : argTypeNames) {
                    final Deque<List<String>> copyArgsTypeNames = new ArrayDeque<>(argsTypeNames);
                    final ArrayList<String> copyCurrentArgsTypeName = new ArrayList<>(currentArgsTypeName);
                    copyCurrentArgsTypeName.add(currentArgTypeName);
                    constructAllPossibleArgsTypeNamesRecursively(copyArgsTypeNames, copyCurrentArgsTypeName, allPossibleArgsTypeNames);
                }
            }

            currentArgsTypeName.add(argTypeName);
            constructAllPossibleArgsTypeNamesRecursively(argsTypeNames, currentArgsTypeName, allPossibleArgsTypeNames);
        }

        return allPossibleArgsTypeNames;
    }
}
