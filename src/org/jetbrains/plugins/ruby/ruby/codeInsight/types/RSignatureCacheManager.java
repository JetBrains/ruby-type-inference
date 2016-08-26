/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.*;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.SymbolPsiProcessor;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.RSymbolTypeImpl;

import java.util.*;
import java.util.stream.Collectors;

abstract class RSignatureCacheManager {
    @NotNull
    private static final Map<String, Pair<RType, Set<RSignature>>> ourSyntheticTypes = new HashMap<>();

    @NotNull
    public abstract List<String> findReturnTypeNamesBySignature(@NotNull final Project project, @Nullable final Module module,
                                                                @NotNull final RSignature signature);

    public abstract void recordSignature(@NotNull final RSignature signature, @NotNull final String returnTypeName);

    public abstract void deleteSignature(@NotNull final RSignature signature);

    public abstract void compact(@NotNull final Project project);

    public abstract void clearCache();

    @NotNull
    RType createTypeByFQNFromStat(@NotNull final Project project, @NotNull final String classFQN) {
        final Set<RSignature> classMethodSignatures = getReceiverMethodSignatures(classFQN);

        if (ourSyntheticTypes.containsKey(classFQN)) {
            final Pair<RType, Set<RSignature>> cached = ourSyntheticTypes.get(classFQN);
            if (cached.getSecond().equals(classMethodSignatures)) {
                return cached.getFirst();
            }
        }

        final Symbol classSymbol = createSyntheticClassSymbol(project, classFQN, classMethodSignatures);
        final RType syntheticType = new RSymbolTypeImpl(classSymbol, Context.INSTANCE);

        ourSyntheticTypes.put(classFQN, Pair.create(syntheticType, classMethodSignatures));
        return syntheticType;
    }

    @NotNull
    public abstract List<ParameterInfo> getMethodArgsInfo(@NotNull final String methodName, @NotNull final String receiverName);

    @NotNull
    protected abstract Set<RSignature> getReceiverMethodSignatures(@NotNull final String receiverName);

    @NotNull
    private static Symbol createSyntheticClassSymbol(@NotNull final Project project,
                                                     @NotNull final String classFQN,
                                                     @NotNull final Set<RSignature> classMethodSignatures) {
        final List<RMethodSymbol> methodSymbols = new ArrayList<>();

        final Symbol classSymbol = new SymbolImpl(project, classFQN, Type.CLASS, null) {
            @NotNull
            @Override
            public Children getChildren() {
                return new ChildrenImpl() {
                    @Override
                    public boolean processChildren(final SymbolPsiProcessor processor, final PsiElement invocationPoint) {
                        for (final RMethodSymbol methodSymbol : methodSymbols) {
                            if (!processor.process(methodSymbol)) {
                                return false;
                            }
                        }
                        return true;
                    }
                };
            }
        };

        methodSymbols.addAll(classMethodSignatures.stream()
                .map(signature -> new RMethodSyntheticSymbol(project,
                                                             signature.getMethodName(),
                                                             Type.INSTANCE_METHOD,
                                                             classSymbol,
                                                             signature.getVisibility(),
                                                             signature.getArgsInfo().stream()
                                                                .map(ParameterInfo::toArgumentInfo)
                                                                .collect(Collectors.toList())))
                .collect(Collectors.toList()));

        return classSymbol;
    }
}
