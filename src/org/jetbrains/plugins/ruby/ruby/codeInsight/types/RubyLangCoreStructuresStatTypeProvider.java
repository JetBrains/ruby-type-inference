package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.RSignatureManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.SqliteRSignatureManager;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.references.RReferenceBase;

public class RubyLangCoreStructuresStatTypeProvider {
    @Nullable
//    @Override
    public RType createTypeByLangCoreConstruction(@NotNull final RReferenceBase ref) {
        if (ref.isConstructorLike()) {
            final RPsiElement receiver = ref.getReceiver();
            if (receiver == null) {
                return null;
            }

            final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
            if (signatureManager == null) {
                return null;
            }

            final String receiverFQN = receiver.getName();
            if (receiverFQN != null) {
                return signatureManager.createTypeByFQNFromStat(receiver.getProject(), receiverFQN);
            }
        }

        return null;
    }
}