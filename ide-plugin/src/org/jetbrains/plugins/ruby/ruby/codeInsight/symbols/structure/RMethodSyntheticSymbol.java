package org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.RType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.controlStructures.methods.RCommandArgumentListImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.controlStructures.methods.RMethodBase;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RMethodSyntheticSymbol extends SymbolImpl implements RMethodSymbol {
    @NotNull
    private final Visibility myVisibility;
    @NotNull
    private final List<ArgumentInfo> myArgsInfo;
    @Nullable
    private final String myPath;

    private final int myLineno;

    @NotNull
    private final NullableLazyValue<PsiElement> declarationElement = new NullableLazyValue<PsiElement>() {
        @Nullable
        @Override
        protected PsiElement compute() {
            if (myPath == null) {
                return null;
            }
            final VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(myPath));
            if (virtualFile == null) {
                return null;
            }
            final PsiFile file = PsiManager.getInstance(getProject()).findFile(virtualFile);
            if (file == null) {
                return null;
            }
            final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
            if (document == null) {
                return null;
            }
            try {
                final int offset = document.getLineStartOffset(myLineno);
                return file.findElementAt(offset);
            } catch (Exception e) {
                return null;
            }

        }
    };

    public RMethodSyntheticSymbol(@NotNull final Project project,
                                  @NotNull final MethodInfo methodInfo,
                                  @NotNull final Type type,
                                  @Nullable final Symbol parent,
                                  @NotNull final List<ArgumentInfo> argsInfo) {
        super(project, methodInfo.getName(), type, parent);
        myVisibility = Visibility.valueOf(methodInfo.getVisibility().name());
        myArgsInfo = argsInfo;
        if (methodInfo.getLocation() != null) {
            myPath = methodInfo.getLocation().getPath();
            myLineno = methodInfo.getLocation().getLineno();
        } else {
            myPath = null;
            myLineno = 0;
        }
    }

    @Override
    public @Nullable List<ArgumentInfo> getArgumentInfos() {
        return myArgsInfo;
    }

    @Nullable
    @Override
    public RType getCallType(@Nullable final RCall call) {
        return null;
    }

    @NotNull
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
        return false;
    }

    @NotNull
    @Override
    public Visibility getVisibility() {
        return myVisibility;
    }

    @Override
    public PsiElement getPsiElement() {
        return declarationElement.getValue();
    }

    @NotNull
    @Override
    public Collection<PsiElement> getAllDeclarations(PsiElement invocationPoint) {
        final PsiElement psiElement = getPsiElement();
        return psiElement == null ? Collections.emptyList() : Collections.singletonList(psiElement);
    }
}