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
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.*;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.SymbolPsiProcessor;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.RSymbolTypeImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class RSignatureCacheManager {
    private static final Map<String, RType> ourSyntheticTypes = new HashMap<>();

    @Nullable
    public abstract String findReturnTypeNameBySignature(@NotNull final RSignature signature, @Nullable final Module module);

    public abstract void recordSignature(@NotNull final RSignature signature, @NotNull final String returnTypeName,
                                         @NotNull final String gemName, @NotNull final String gemVersion);

    public abstract void clearCache();

    @NotNull
    public RType createTypeByFQNFromStat(@NotNull final Project project, @NotNull final String classFQN) {
        if (ourSyntheticTypes.containsKey(classFQN)) {
            return ourSyntheticTypes.get(classFQN);
        }

        final List<RSignature> classMethodSignatures = getReceiverMethodSignatures(classFQN);
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
                                                             signature.getArgsInfo()))
                .collect(Collectors.toList()));

        final RType syntheticType = new RSymbolTypeImpl(classSymbol, Context.INSTANCE);
        // ourSyntheticTypes.put(classFQN, syntheticType); TODO: Clear ourSyntheticTypes on run

        return syntheticType;
    }

    @Nullable
    public abstract List<ArgumentInfo> getMethodArgsInfo(@NotNull final String methodName, @Nullable String receiverName);

    @NotNull
    protected abstract List<RSignature> getReceiverMethodSignatures(@NotNull final String receiverName);
}
