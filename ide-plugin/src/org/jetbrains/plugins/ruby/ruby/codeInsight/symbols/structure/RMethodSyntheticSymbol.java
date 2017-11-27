package org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.rdoc.yard.psi.RangeInDocumentFakePsiElement;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.RType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.controlStructures.methods.RCommandArgumentListImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo;
import org.jetbrains.ruby.stateTracker.Location;
import org.jetbrains.ruby.stateTracker.RubyMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RMethodSyntheticSymbol extends SymbolImpl implements RMethodSymbol {
    @NotNull
    private final Visibility myVisibility;
    @NotNull
    private final List<ArgumentInfo> myArgsInfo;
    @Nullable
    private final String myPath;

    private final int myLineno;

    public RMethodSyntheticSymbol(@NotNull final Project project,
                                  @NotNull final Type type,
                                  @NotNull final RubyMethod rubyMethod,
                                  @Nullable final Symbol parent) {
        super(project, rubyMethod.getName(), type, parent);
        myVisibility = Visibility.PUBLIC;
        myArgsInfo = toArgsInfo(rubyMethod.getArguments());
        final Location location = rubyMethod.getLocation();
        if (location != null) {
            myPath = location.getPath();
            myLineno = location.getLineNo();
        } else {
            myPath = "";
            myLineno = 0;
        }
    }

    private List<ArgumentInfo> toArgsInfo(List<RubyMethod.ArgInfo> arguments) {
        return arguments.stream().map(RMethodSyntheticSymbol::toArgumentInfo).collect(Collectors.toList());
    }

    private static ArgumentInfo toArgumentInfo(final @NotNull RubyMethod.ArgInfo argInfo) {
        ArgumentInfo.Type type;
        switch (argInfo.getKind()) {
            case REQ:
                type = ArgumentInfo.Type.SIMPLE;
                break;
            case OPT:
                type = ArgumentInfo.Type.PREDEFINED;
                break;
            case REST:
                type = ArgumentInfo.Type.ARRAY;
                break;
            case KEY:
                type = ArgumentInfo.Type.NAMED;
                break;
            case KEY_REST:
                type = ArgumentInfo.Type.HASH;
                break;
            case KEY_REQ:
                type = ArgumentInfo.Type.KEYREQ;
                break;
            case BLOCK:
                type = ArgumentInfo.Type.BLOCK;
                break;
            default:
                throw new IllegalArgumentException(argInfo.getKind().toString());
        }
        return new ArgumentInfo(argInfo.getName(), type);
    }

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
    public @Nullable
    List<ArgumentInfo> getArgumentInfos() {
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

        return CachedValuesManager.getCachedValue(file, () ->
                CachedValueProvider.Result.create(calcElement(file), file));
    }

    @NotNull
    @Override
    public Collection<PsiElement> getAllDeclarations(PsiElement invocationPoint) {
        final PsiElement psiElement = getPsiElement();
        return psiElement == null ? Collections.emptyList() : Collections.singletonList(psiElement);
    }

    @Nullable
    private PsiElement calcElement(@NotNull PsiFile file) {
        final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        if (document == null) {
            return null;
        }
        return ReadAction.compute(() -> {
            int offset = document.getLineStartOffset(myLineno);
            int nextLineOffset = document.getLineEndOffset(myLineno);
            int curOffset = offset;
            PsiElement psiElement;
            do {
                psiElement = file.findElementAt(curOffset);
                if (psiElement == null) {
                    return null;
                }
                curOffset = psiElement.getTextRange().getEndOffset();
            } while (!(psiElement instanceof RPsiElement) && curOffset < nextLineOffset);

            if (psiElement instanceof RPsiElement) {
                return psiElement;
            }

            psiElement = file.findElementAt(offset);
            if (psiElement == null) {
                return null;
            }
            final int startElementOffset = psiElement.getTextRange().getStartOffset();
            final int endElementOffset = psiElement.getTextRange().getEndOffset();
            int start = offset - startElementOffset;
            int end = Math.min(nextLineOffset - startElementOffset, endElementOffset - startElementOffset);
            return new RangeInDocumentFakePsiElement(psiElement, new TextRange(start, end));
        });
    }
}