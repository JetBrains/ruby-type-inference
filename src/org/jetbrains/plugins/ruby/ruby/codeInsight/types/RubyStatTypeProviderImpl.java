package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArgumentToBlock;
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
            final RSignatureCacheManager cacheManager = SqliteRSignatureCacheManager.getInstance();
            if (cacheManager == null) {
                return null;
            }

            final Module module = ModuleUtilCore.findModuleForPsiElement(call);
            final String receiverName = StringUtil.notNullize(names.getSecond(), CoreTypes.Object);
            final List<ArgumentInfo> argsInfo = cacheManager.getMethodArgsInfo(methodName, receiverName);
            final List<List<String>> argsTypeNames = getArgsTypeNames(call.getParent(), argsInfo, callArgs);
            final List<RType> returnTypes = argsTypeNames.stream()
                    .map(argsTypeName -> new RSignature(methodName, receiverName, Visibility.PUBLIC, argsInfo, argsTypeName))
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
                                                         @NotNull final RSignatureCacheManager cacheManager,
                                                         @NotNull final RSignature signature) {
        final List<String> returnTypeNames = cacheManager.findReturnTypeNamesBySignature(module, signature);
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
                if (receiver != null) {
                    final Symbol receiverSymbol = ResolveUtil.resolveToSymbolWithCaching(receiver.getReferenceEx(false));
                    receiverFQN = receiverSymbol != null
                            ? SymbolUtil.getSymbolFullQualifiedName(receiverSymbol)
                            : receiver.getName();
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
    private static List<List<String>> getArgsTypeNames(@NotNull final PsiElement callParent,
                                                       @NotNull final List<ArgumentInfo> argsInfo,
                                                       @NotNull final List<RPsiElement> args) {
        final List<List<String>> argsTypeNames = new ArrayList<>();
        constructAllArgsTypeNamesRecursively(new LinkedList<>(args), new ArrayList<>(), argsTypeNames);

        for (final List<String> argsTypeName: argsTypeNames) {
            appendBlockArgIfNeeded(callParent, argsInfo, argsTypeName);
        }

        return argsTypeNames;
    }

    private static void constructAllArgsTypeNamesRecursively(@NotNull final Deque<RPsiElement> args,
                                                             @NotNull final List<String> currentArgsTypeName,
                                                             @NotNull final List<List<String>> argsTypeNames) {
        if (args.isEmpty()) {
            argsTypeNames.add(currentArgsTypeName);
        } else {
            RPsiElement arg = args.pop();

            if (arg instanceof RAssoc) {
                arg = ((RAssoc) arg).getValue();
            }

            if (arg instanceof RExpression) {
                final RType type = ((RExpression) arg).getType();
                processTypeDuringConstructionArgsTypeNames(args, currentArgsTypeName, argsTypeNames, type);
            } else if (arg instanceof RArgumentToBlock) {
                currentArgsTypeName.add(CoreTypes.Proc);
                constructAllArgsTypeNamesRecursively(args, currentArgsTypeName, argsTypeNames);
            }
        }
    }

    private static void processTypeDuringConstructionArgsTypeNames(@NotNull final Deque<RPsiElement> args,
                                                                   @NotNull final List<String> currentArgsTypeName,
                                                                   @NotNull final List<List<String>> argsTypeNames,
                                                                   @NotNull final RType type) {
        if (type instanceof RUnionType) {
            final ArrayList<String> copyCurrentArgsTypeName = new ArrayList<>(currentArgsTypeName);
            final Deque<RPsiElement> copyArgs = new LinkedList<>(args);
            final RUnionType unionType = (RUnionType) type;
            processTypeDuringConstructionArgsTypeNames(args, currentArgsTypeName, argsTypeNames, unionType.getType1());
            processTypeDuringConstructionArgsTypeNames(copyArgs, copyCurrentArgsTypeName, argsTypeNames, unionType.getType2());
        } else {
            currentArgsTypeName.add(type.getName());
            constructAllArgsTypeNamesRecursively(args, currentArgsTypeName, argsTypeNames);
        }
    }

    private static void appendBlockArgIfNeeded(@NotNull final PsiElement callParent,
                                               @NotNull final List<ArgumentInfo> argsInfo,
                                               @NotNull final List<String> argsTypeName) {
        if (!argsInfo.isEmpty() && argsInfo.get(argsInfo.size() - 1).getType() == ArgumentInfo.Type.BLOCK &&
            (argsTypeName.isEmpty() || !argsTypeName.get(argsTypeName.size() - 1).equals(CoreTypes.Proc))) {
            argsTypeName.add(callParent instanceof RBlockCall ? CoreTypes.Proc : CoreTypes.NilClass);
        }
    }


}
