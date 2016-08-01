package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;

import java.util.List;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {
    @Override
    @Nullable
    public RType createTypeByCallAndArgs(@NotNull RExpression call, @NotNull List<RPsiElement> callArgs) {
        RSignature signature = RSignatureUtil.getRSignatureByRExpressionAndArgs(call, callArgs);
        if (signature != null) {
            RSignatureCacheManager cacheManager = HashMapRSignatureCacheManager.getInstance();

            String returnTypeName = cacheManager.findReturnTypeNameBySignature(signature);
            if (returnTypeName != null) {
                return RTypeFactory.createTypeByFQN(call.getProject(), returnTypeName);
            }
        }

        return null;
    }
}