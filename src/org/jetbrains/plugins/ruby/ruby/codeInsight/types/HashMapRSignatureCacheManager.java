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

    private final Map<RSignature, String> myCache = new HashMap<>();

    public static RSignatureCacheManager getInstance() {
        return INSTANCE;
    }

    private HashMapRSignatureCacheManager() {
        myCache.put(
                new RSignature(
                        "test_eval_class",
                        "Object",
                        new ArrayList<String>() {{
                            add(CoreTypes.String);
                        }}
                ),
                "EvalClass"
        );
        myCache.put(
                new RSignature(
                        "bar",
                        "Qwerty",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Array
        );
        myCache.put(
                new RSignature(
                        "bar",
                        "Qwerty",
                        new ArrayList<>()
                ),
                CoreTypes.Bigdecimal
        );
        myCache.put(
                new RSignature(
                        "baz",
                        "Qwerty",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Bignum
        );
        myCache.put(
                new RSignature(
                        "baz",
                        "Qwerty",
                        new ArrayList<>()
                ),
                CoreTypes.Class
        );
        myCache.put(
                new RSignature(
                        "bar",
                        "qwe",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Complex
        );
        myCache.put(
                new RSignature(
                        "bar",
                        "qwe",
                        new ArrayList<>()
                ),
                CoreTypes.Enumerable
        );
        myCache.put(
                new RSignature(
                        "baz",
                        "qwe",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Exception
        );
        myCache.put(
                new RSignature(
                        "baz",
                        "qwe",
                        new ArrayList<>()
                ),
                CoreTypes.Fixnum
        );
        myCache.put(
                new RSignature(
                        "bar",
                        "Object",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.Float
        );
        myCache.put(
                new RSignature(
                        "bar",
                        "Object",
                        new ArrayList<>()
                ),
                CoreTypes.Hash
        );
        myCache.put(
                new RSignature(
                        "baz",
                        "Object",
                        new ArrayList<String>() {{
                            add(CoreTypes.Fixnum);
                        }}
                ),
                CoreTypes.IO
        );
        myCache.put(
                new RSignature(
                        "baz",
                        "Object",
                        new ArrayList<>()
                ),
                CoreTypes.Time
        );
    }

    @Override
    @Nullable
    public String findReturnTypeNameBySignature(@NotNull final RSignature signature) {
        return myCache.containsKey(signature) ? myCache.get(signature) : null;
    }

    @Override
    public void recordSignature(@NotNull final RSignature signature, @NotNull final String returnTypeName) {
        myCache.put(signature, returnTypeName);
    }

    @Override
    public void clearCache() {
        myCache.clear();
    }
}
