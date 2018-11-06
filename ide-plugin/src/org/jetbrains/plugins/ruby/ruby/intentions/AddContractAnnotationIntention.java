package org.jetbrains.plugins.ruby.ruby.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.exposed.dao.EntityID;
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManagerKt;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RFile;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyElementFactory;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RFName;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddContractAnnotationIntention extends BaseRubyMethodIntentionAction {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
        return getText();
    }

    public boolean isAvailable(@NotNull final Project project, final @NotNull Editor editor, @NotNull final PsiFile file) {
        if (!super.isAvailable(project, editor, file)) {
            return false;
        }
        if (!file.isWritable()) {
            return false;
        }
        RFName rfName = getRFName(editor, file);
        if (rfName == null) {
            return false;
        }
        RMethod method = RubyPsiUtil.getContainingRMethod(rfName);
        if (method == null) {
            return false;
        }

        EntityID<Integer> found = ThreadLocalTransactionManagerKt.transaction(null,
                transaction -> MethodInfoTable.INSTANCE.findRowId(createMethodInfo(method)));
        return found != null;
    }

    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        final RMethod method = PsiTreeUtil.getParentOfType(element, RMethod.class);
        assert method != null : "Method cannot be null here";
        final MethodInfo methodInfo = createMethodInfo(method);
        assert methodInfo != null : "MethodInfo mustn't be null: it was checked in isAvailable";

        List<CallInfo> registeredCallInfos = RSignatureProviderImpl.INSTANCE.getRegisteredCallInfos(methodInfo);

        StringBuilder builder = new StringBuilder();
        builder.append("# @contract");
        for (CallInfo callInfo : registeredCallInfos) {
            builder.append("\n# (");
            builder.append(String.join(", ", callInfo.getNamedArguments().stream().map(ArgumentNameAndType::getType).toArray(String[]::new)));
            builder.append(") -> ");
            builder.append(callInfo.getReturnType());
        }

        final RFile fileWithComments = RubyElementFactory.createRubyFile(project, builder.toString());
        method.getParent().addRangeBefore(fileWithComments.getFirstChild(), fileWithComments.getLastChild(), method);
    }

    @Nullable
    private static MethodInfo createMethodInfo(@NotNull RMethod method) {
        final FQN fqnWithNesting = method.getFQNWithNesting();
        final String methodName = fqnWithNesting.getShortName();
        final String receiverName = fqnWithNesting.getCallerFQN().getFullPath();
        final Module module = ModuleUtilCore.findModuleForPsiElement(method);
        if (module == null) {
            return null;
        }
        return new MethodInfo.Impl(new ClassInfo.Impl(null, receiverName), methodName, RVisibility.PUBLIC, null);
    }

    private void dfs(@NotNull SignatureNode v, @NotNull StringBuilder currentLine, @NotNull List<String> result) {
        if (result.size() > 3) {
            return;
        }
        if (v.getTransitions().isEmpty()) {
            result.add(currentLine.toString());
            return;
        }
        if (v.getTransitions().size() == 1) {
            v.getTransitions().forEach((edge, node) -> dfs(node, addEdge(currentLine, edgeToStr(edge)), result));
            return;
        }
        Map<SignatureNode, List<String>> transitions = new HashMap<>();
        v.getTransitions().forEach((edge, node) -> transitions.computeIfAbsent(node, (a) -> new ArrayList<>()).add(edgeToStr(edge)));
        transitions.forEach((node, types) -> dfs(node, addEdge(new StringBuilder(currentLine), StringUtil.join(types, " | ")), result));
    }

    private StringBuilder addEdge(@NotNull StringBuilder currentLine, String types) {
        return currentLine.append(currentLine.length() == 0 ? "" : ", ").append(types);
    }

    private String edgeToStr(ContractTransition edge) {
        if (edge instanceof ReferenceContractTransition) {
            return "T" + (((ReferenceContractTransition) edge).getMask() + 1);
        } else {
            return ((TypedContractTransition) edge).getType();
        }
    }

    @NotNull
    @Override
    protected String getTextByRubyFunctionNamePsiElement(@Nullable RFName element) {
        return "Add type contract";
    }
}