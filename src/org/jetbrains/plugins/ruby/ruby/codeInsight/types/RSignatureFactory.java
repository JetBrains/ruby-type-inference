package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier;

import java.util.List;
import java.util.stream.Collectors;

public class RSignatureFactory {
    private RSignatureFactory() {
    }

    @Nullable
    public static RSignature createSignatureByExpressionAndArgs(@NotNull RExpression call,
                                                                @NotNull List<RPsiElement> args) {
        final PsiElement element2resolve = call instanceof RCall ? ((RCall) call).getPsiCommand() : call;
        final Symbol symbol = ResolveUtil.resolveToSymbolWithCaching(element2resolve.getReference(), false);
        if (symbol != null) {
            return createSignatureBySymbolAndArgs(symbol, args);
        } else if (element2resolve instanceof RReference) {
            return createSignatureByReferenceAndArgs((RReference) element2resolve, args);
        } else {
            return createSignatureByIdentifierAndArgs((RIdentifier) element2resolve, args);
        }
    }

    @Nullable
    public static RSignature createSignatureBySymbolAndArgs(@NotNull Symbol methodSymbol,
                                                            @NotNull List<RPsiElement> args) {
        String methodFQN = SymbolUtil.getSymbolFullQualifiedName(methodSymbol);
        if (methodFQN != null) {
            final List<String> argsTypeName = args.stream()
                    .map(arg -> ((RExpression) arg).getType().getPresentableName())
                    .collect(Collectors.toList());

            return new RSignature(methodFQN, argsTypeName);
        }

        return null;
    }

    @Nullable
    public static RSignature createSignatureByReferenceAndArgs(@NotNull RReference methodRef,
                                                               @NotNull List<RPsiElement> args) {
        if (methodRef.getName() != null) {
            final List<String> argsTypeName = args.stream()
                    .map(arg -> ((RExpression) arg).getType().getPresentableName())
                    .collect(Collectors.toList());

            final RPsiElement receiver = methodRef.getReceiver();
            if (receiver != null) {
                final Symbol symbol = ResolveUtil.resolveToSymbolWithCaching(receiver.getReferenceEx(false));
                final String receiverFQN = symbol != null ? SymbolUtil.getSymbolFullQualifiedName(symbol) : receiver.getName();
                if (receiverFQN != null) {
                    final String methodFQN = receiverFQN + "." + methodRef.getName();
                    return new RSignature(methodFQN, argsTypeName);
                }
            }

            return new RSignature(methodRef.getName(), argsTypeName);
        }

        return null;
    }

    @Nullable
    public static RSignature createSignatureByIdentifierAndArgs(@NotNull RIdentifier methodId,
                                                                @NotNull List<RPsiElement> args) {
        if (methodId.getName() != null) {
            final List<String> argsTypeName = args.stream()
                    .map(arg -> ((RExpression) arg).getType())
                    .map(RType::getPresentableName)
                    .collect(Collectors.toList());

            final Symbol scopeContext = SymbolUtil.getScopeContext(methodId);
            if (scopeContext != null && SymbolUtil.isClassOrModuleSymbol(scopeContext.getType())) {
                final String receiverFQN = SymbolUtil.getSymbolFullQualifiedName(scopeContext);
                if (receiverFQN != null) {
                    final String methodFQN = receiverFQN + "." + methodId.getName();
                    return new RSignature(methodFQN, argsTypeName);
                }
            }

            return new RSignature(methodId.getName(), argsTypeName);
        }

        return null;
    }
}

