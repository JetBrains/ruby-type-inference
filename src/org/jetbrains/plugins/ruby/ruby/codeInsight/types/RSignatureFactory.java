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
    public static RSignature createSignatureByExpressionAndArgs(@NotNull final RExpression call,
                                                                @NotNull final List<RPsiElement> args) {
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
    public static RSignature createSignatureBySymbolAndArgs(@NotNull final Symbol methodSymbol,
                                                            @NotNull final List<RPsiElement> args) {
        final String methodFQN = SymbolUtil.getSymbolFullQualifiedName(methodSymbol);
        if (methodFQN != null) {
            final List<String> argsTypeName = args.stream()
                    .map(arg -> ((RExpression) arg).getType())
                    .map(RType::getPresentableName)
                    .collect(Collectors.toList());

            final int separatorIndex = methodFQN.lastIndexOf(".");
            final String receiverFQN = separatorIndex >= 0 ? methodFQN.substring(0, separatorIndex) : null;
            return new RSignature(methodSymbol.getName(), receiverFQN, argsTypeName);
        }

        return null;
    }

    @Nullable
    public static RSignature createSignatureByReferenceAndArgs(@NotNull final RReference methodRef,
                                                               @NotNull final List<RPsiElement> args) {
        if (methodRef.getName() != null) {
            final List<String> argsTypeName = args.stream()
                    .map(arg -> ((RExpression) arg).getType())
                    .map(RType::getPresentableName)
                    .collect(Collectors.toList());

            final RPsiElement receiver = methodRef.getReceiver();
            if (receiver != null) {
                final Symbol receiverSymbol = ResolveUtil.resolveToSymbolWithCaching(receiver.getReferenceEx(false));
                final String receiverFQN = receiverSymbol != null ? SymbolUtil.getSymbolFullQualifiedName(receiverSymbol) : receiver.getName();
                return new RSignature(methodRef.getName(), receiverFQN, argsTypeName);
            }

            return new RSignature(methodRef.getName(), null, argsTypeName);
        }

        return null;
    }

    @Nullable
    public static RSignature createSignatureByIdentifierAndArgs(@NotNull final RIdentifier methodId,
                                                                @NotNull final List<RPsiElement> args) {
        if (methodId.getName() != null) {
            final List<String> argsTypeName = args.stream()
                    .map(arg -> ((RExpression) arg).getType())
                    .map(RType::getPresentableName)
                    .collect(Collectors.toList());

            final Symbol receiverSymbol = SymbolUtil.getScopeContext(methodId);
            if (receiverSymbol != null && SymbolUtil.isClassOrModuleSymbol(receiverSymbol.getType())) {
                final String receiverFQN = SymbolUtil.getSymbolFullQualifiedName(receiverSymbol);
                return new RSignature(methodId.getName(), receiverFQN, argsTypeName);
            }

            return new RSignature(methodId.getName(), null, argsTypeName);
        }

        return null;
    }
}

