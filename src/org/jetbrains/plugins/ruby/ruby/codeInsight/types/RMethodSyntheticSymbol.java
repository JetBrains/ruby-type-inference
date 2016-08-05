package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSymbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.controlStructures.methods.RCommandArgumentListImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.controlStructures.methods.RMethodBase;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;

import java.util.List;

public class RMethodSyntheticSymbol extends SymbolImpl implements RMethodSymbol {
    @NotNull
    private final List<ArgumentInfo> myArgsInfo;

    public RMethodSyntheticSymbol(final Project project,
                                  @Nullable final String name,
                                  final Type type,
                                  @Nullable final Symbol parent,
                                  @NotNull final List<ArgumentInfo> argsInfo) {
        super(project, name, type, parent);
        myArgsInfo = argsInfo;
    }

    @Override
    public int getMinParameterCount(final PsiElement invocationPoint) {
        return RMethodBase.getMinNumberOfArgumentsFromArgumentInfos(myArgsInfo);
    }

    @Override
    public int getMaxParameterCount(final PsiElement invocationPoint) {
        return RMethodBase.getMaxNumberOfArgumentsFromArgumentInfos(myArgsInfo);
    }

    @Override
    public boolean hasCodeBlockArgument() {
        return !myArgsInfo.isEmpty() && myArgsInfo.get(myArgsInfo.size() - 1).getType() == ArgumentInfo.Type.BLOCK;
    }

    @Override
    public RType getCallType(final RCall call) {
        return null;
    }

    @Override
    public String getArgsPresentation() {
        if (myArgsInfo.isEmpty()) {
            return "";
        } else {
            return "(" + RCommandArgumentListImpl.getPresentableName(myArgsInfo) + ")";
        }
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    @NotNull
    public Visibility getVisibility() {
        return Visibility.PUBLIC;
    }
}
