package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.psi.PsiElement;
import org.antlr.v4.runtime.misc.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArgumentToBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RSignature {
    private final String myMethodName;
    private final String myReceiverName;
    private final List<String> myArgsTypeName;
    private final List<ArgumentInfo> myArgsInfo;

    @NotNull
    public static Pair<String, String> getMethodAndReceiverNames(RExpression call) {
        final PsiElement element2resolve = call instanceof RCall ? ((RCall) call).getPsiCommand() : call;
        final Symbol methodSymbol = ResolveUtil.resolveToSymbolWithCaching(element2resolve.getReference(), false);
        if (methodSymbol != null) {
            final String methodFQN = SymbolUtil.getSymbolFullQualifiedName(methodSymbol);
            if (methodFQN != null) {
                final int separatorIndex = methodFQN.lastIndexOf(".");
                final String receiverFQN = separatorIndex >= 0 ? methodFQN.substring(0, separatorIndex) : null;
                return new Pair<>(methodSymbol.getName(), receiverFQN);
            }
        } else if (element2resolve instanceof RReference) {
            final RReference methodRef = (RReference) element2resolve;
            if (methodRef.getName() != null) {
                final RPsiElement receiver = methodRef.getReceiver();
                if (receiver != null) {
                    final Symbol receiverSymbol = ResolveUtil.resolveToSymbolWithCaching(receiver.getReferenceEx(false));
                    final String receiverFQN = receiverSymbol != null ? SymbolUtil.getSymbolFullQualifiedName(receiverSymbol) : receiver.getName();
                    return new Pair<>(methodRef.getName(), receiverFQN);
                }

                return new Pair<>(methodRef.getName(), null);
            }
        } else if (element2resolve instanceof RIdentifier) {
            RIdentifier methodId = (RIdentifier) element2resolve;
            if (methodId.getName() != null) {
                final Symbol receiverSymbol = SymbolUtil.getScopeContext(methodId);
                if (receiverSymbol != null && SymbolUtil.isClassOrModuleSymbol(receiverSymbol.getType())) {
                    final String receiverFQN = SymbolUtil.getSymbolFullQualifiedName(receiverSymbol);
                    return new Pair<>(methodId.getName(), receiverFQN);
                }

                return new Pair<>(methodId.getName(), null);
            }
        }

        return new Pair<>(call.getName(), null);
    }

    @NotNull
    public static List<String> getArgsTypeName(@NotNull final List<RPsiElement> args) {
        return args.stream()
                .map(arg -> {
                    if (arg instanceof RExpression) {
                        final RType type = ((RExpression) arg).getType();
                        return type.getPresentableName();
                    } else if (arg instanceof RAssoc) {
                        final RPsiElement value = ((RAssoc) arg).getValue();
                        if (value instanceof RExpression) {
                            final RType type = ((RExpression) value).getType();
                            return type.getPresentableName();
                        }
                    } else if (arg instanceof RArgumentToBlock) {
                        return CoreTypes.Proc;
                    }
                    return null;
                })
                .collect(Collectors.toList());
    }

    public RSignature(@NotNull final String methodName,
                      @NotNull final String receiverName,
                      @NotNull final List<String> argsTypeName,
                      @Nullable final List<ArgumentInfo> argsInfo) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName;
        this.myArgsTypeName = argsTypeName;
        this.myArgsInfo = argsInfo != null ? argsInfo : new ArrayList<>();
    }

    @NotNull
    public String getMethodName() {
        return myMethodName;
    }

    @NotNull
    public String getReceiverName() {
        return myReceiverName;
    }

    @NotNull
    public List<String> getArgsTypeName() {
        return myArgsTypeName;
    }

    @NotNull
    public List<ArgumentInfo> getArgsInfo() {
        return myArgsInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignature that = (RSignature) o;

        if (!myMethodName.equals(that.myMethodName)) return false;
        if (!myReceiverName.equals(that.myReceiverName)) return false;
        return myArgsTypeName.equals(that.myArgsTypeName);
    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        return result;
    }
}
