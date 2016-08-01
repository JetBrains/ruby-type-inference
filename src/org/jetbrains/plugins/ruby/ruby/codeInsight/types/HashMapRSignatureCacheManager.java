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

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;

public class HashMapRSignatureCacheManager extends RSignatureCacheManager {
    private static final RSignatureCacheManager INSTANCE = new HashMapRSignatureCacheManager();

    private final Map<RSignature, String> cache = new HashMap<>();

    public static RSignatureCacheManager getInstance() {
        // TODO?: return ServiceManager.getService(project, HashMapRSignatureCacheManager.class);
        return INSTANCE;
    }

    private HashMapRSignatureCacheManager() {
        cache.put(
                new RSignature(
                        "Qwerty.bar",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Array
        );
        cache.put(
                new RSignature(
                        "Qwerty.bar",
                        new ArrayList<>()
                ),
                CoreTypes.Bigdecimal
        );
        cache.put(
                new RSignature(
                        "Qwerty.baz",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Bignum
        );
        cache.put(
                new RSignature(
                        "Qwerty.baz",
                        new ArrayList<>()
                ),
                CoreTypes.Class
        );
        cache.put(
                new RSignature(
                        "qwe.bar",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Complex
        );
        cache.put(
                new RSignature(
                        "qwe.bar",
                        new ArrayList<>()
                ),
                CoreTypes.Enumerable
        );
        cache.put(
                new RSignature(
                        "qwe.baz",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Exception
        );
        cache.put(
                new RSignature(
                        "qwe.baz",
                        new ArrayList<>()
                ),
                CoreTypes.Fixnum
        );
        cache.put(
                new RSignature(
                        "bar",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Float
        );
        cache.put(
                new RSignature(
                        "bar",
                        new ArrayList<>()
                ),
                CoreTypes.Hash
        );
        cache.put(
                new RSignature(
                        "baz",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.IO
        );
        cache.put(
                new RSignature(
                        "baz",
                        new ArrayList<>()
                ),
                CoreTypes.Time
        );
    }

    @Override
    @Nullable
    public String findReturnTypeNameBySignature(@NotNull RSignature signature) {
        return cache.containsKey(signature) ? cache.get(signature) : null;
    }

    @Override
    public void recordSignature(@NotNull RSignature signature, @NotNull String returnTypeName) {
        cache.put(signature, returnTypeName);
    }

    @Override
    public void clearCache() {
        cache.clear();
    }
}
