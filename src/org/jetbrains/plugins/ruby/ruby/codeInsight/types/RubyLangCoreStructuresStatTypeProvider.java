package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.cacheManager.RSignatureCacheManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.cacheManager.SqliteRSignatureCacheManager;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.references.RReferenceBase;

public class RubyLangCoreStructuresStatTypeProvider implements RubyLangCoreStructuresTypeProvider {
    @Nullable
    @Override
    public RType createTypeByLangCoreConstruction(@NotNull final RReferenceBase ref) {
        if (ref.isConstructorLike()) {
            final RPsiElement receiver = ref.getReceiver();
            if (receiver == null) {
                return null;
            }

            final RSignatureCacheManager cacheManager = SqliteRSignatureCacheManager.getInstance();
            if (cacheManager == null) {
                return null;
            }

            final String receiverFQN = receiver.getName();
            if (receiverFQN != null) {
                return cacheManager.createTypeByFQNFromStat(receiver.getProject(), receiverFQN);
            }
        }

        return null;
    }
}