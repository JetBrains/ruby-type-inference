package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
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

import java.util.List;
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

            final String receiverName = StringUtil.notNullize(names.getSecond(), CoreTypes.Object);
            final List<ArgumentInfo> argsInfo = cacheManager.getMethodArgsInfo(methodName, receiverName);
            final List<String> argsTypeName = getArgsTypeName(callArgs);
            appendBlockArgIfNeeded(call, argsInfo, argsTypeName);

            final Module module = ModuleUtilCore.findModuleForPsiElement(call);
            final RSignature signature = new RSignature(methodName, receiverName, Visibility.PUBLIC, argsInfo, argsTypeName);
            final String returnTypeName = cacheManager.findReturnTypeNameBySignature(module, signature);
            if (returnTypeName != null) {
                RType returnType = RTypeFactory.createTypeByFQN(call.getProject(), returnTypeName);
                if (returnType == REmptyType.INSTANCE) {
                    returnType = cacheManager.createTypeByFQNFromStat(call.getProject(), returnTypeName);
                }

                return returnType;
            }
        }

        return null;
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
    private static List<String> getArgsTypeName(@NotNull final List<RPsiElement> args) {
        return args.stream()
                .map(arg -> {
                    if (arg instanceof RAssoc) {
                        arg = ((RAssoc) arg).getValue();
                    }
                    if (arg instanceof RExpression) {
                        final RType type = ((RExpression) arg).getType();
                        return type.getPresentableName(); // TODO: fix and handle booleans
                    } else if (arg instanceof RArgumentToBlock) {
                        return CoreTypes.Proc;
                    }
                    return null;
                })
                .collect(Collectors.toList());
    }

    private static void appendBlockArgIfNeeded(@NotNull final RExpression call,
                                               @NotNull final List<ArgumentInfo> argsInfo,
                                               @NotNull final List<String> argsTypeName) {
        if (!argsInfo.isEmpty() && argsInfo.get(argsInfo.size() - 1).getType() == ArgumentInfo.Type.BLOCK &&
            (argsTypeName.isEmpty() || !argsTypeName.get(argsTypeName.size() - 1).equals(CoreTypes.Proc))) {
            argsTypeName.add(call.getParent() instanceof RBlockCall ? CoreTypes.Proc : CoreTypes.NilClass);
        }
    }


}
