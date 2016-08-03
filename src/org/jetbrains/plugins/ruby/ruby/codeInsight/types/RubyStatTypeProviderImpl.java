package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;

import java.util.List;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {
    @Override
    @Nullable
    public RType createTypeByCallAndArgs(@NotNull final RExpression call, @NotNull final List<RPsiElement> callArgs) {
        final RSignature signature = RSignatureFactory.createSignatureByExpressionAndArgs(call, callArgs);
        if (signature != null) {
            final RSignatureCacheManager cacheManager = SqliteRSignatureCacheManager.getInstance();

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