package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.antlr.v4.runtime.misc.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;

import java.util.List;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {
    @Override
    @Nullable
    public RType createTypeByCallAndArgs(@NotNull final RExpression call, @NotNull final List<RPsiElement> callArgs) {
        final Pair<String, String> names = RSignature.getMethodAndReceiverNames(call);
        final String methodName = names.a;

        if (methodName != null) {
            final String receiverName = names.b != null ? names.b : CoreTypes.Object;
            final RSignatureCacheManager cacheManager = SqliteRSignatureCacheManager.getInstance();

            final List<ArgumentInfo> argsInfo = cacheManager.getMethodArgsInfo(methodName, receiverName);
            final List<String> argsTypeName = RSignature.getArgsTypeName(callArgs);
            if (argsInfo != null && !argsInfo.isEmpty() && argsInfo.get(argsInfo.size() - 1).getType() == ArgumentInfo.Type.BLOCK &&
                (argsTypeName.isEmpty() || !argsTypeName.get(argsTypeName.size() - 1).equals(CoreTypes.Proc))) {
                argsTypeName.add(call.getParent() instanceof RBlockCall ? CoreTypes.Proc : CoreTypes.NilClass);
            }

            final RSignature signature = new RSignature(methodName, receiverName, argsTypeName, argsInfo);
            final String returnTypeName = cacheManager.findReturnTypeNameBySignature(signature);
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
}
