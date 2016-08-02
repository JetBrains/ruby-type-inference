package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.*;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.SymbolPsiProcessor;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.RSymbolTypeImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {
    final static private Map<String, RType> syntheticTypes = new HashMap<>();

    @NotNull
    private static RType getSyntheticRTypeByClassNameFromStat(Project project, String className) {
        if (syntheticTypes.containsKey(className)) {
            return syntheticTypes.get(className);
        }

        final List<RMethodSymbol> methodSymbols = new ArrayList<>();

        final Symbol klass = new SymbolImpl(project, className, Type.CLASS, null) {
            @NotNull
            @Override
            public Children getChildren() {
                return new ChildrenImpl() {
                    @Override
                    public boolean processChildren(final SymbolPsiProcessor processor, final PsiElement invocationPoint) {
                        for (RMethodSymbol methodSymbol : methodSymbols) {
                            if (!processor.process(methodSymbol)) {
                                return false;
                            }
                        }
                        return true;
                    }
                };
            }
        };

        methodSymbols.add(new RTypedSyntheticSymbol(project,
                                                    "foo",
                                                    Type.INSTANCE_METHOD,
                                                    klass,
                                                    REmptyType.INSTANCE,
                                                    -1));
        methodSymbols.add(new RTypedSyntheticSymbol(project,
                                                    "bar",
                                                    Type.INSTANCE_METHOD,
                                                    klass,
                                                    REmptyType.INSTANCE,
                                                    -1));

        RType syntheticType = new RSymbolTypeImpl(klass, Context.INSTANCE);
        syntheticTypes.put(className, syntheticType);

        return syntheticType;
    }

    @Override
    @Nullable
    public RType createTypeByCallAndArgs(@NotNull RExpression call, @NotNull List<RPsiElement> callArgs) {
        RSignature signature = RSignatureFactory.createSignatureByExpressionAndArgs(call, callArgs);
        if (signature != null) {
            RSignatureCacheManager cacheManager = HashMapRSignatureCacheManager.getInstance();

            String returnTypeName = cacheManager.findReturnTypeNameBySignature(signature);
            if (returnTypeName != null) {
                RType returnType = RTypeFactory.createTypeByFQN(call.getProject(), returnTypeName);
                if (returnType == REmptyType.INSTANCE) {
                    returnType = getSyntheticRTypeByClassNameFromStat(call.getProject(), returnTypeName);
                }

                return returnType;
            }
        }

        return null;
    }
}