package org.jetbrains.plugins.ruby.ruby.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
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
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.CoreTypes;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.RubyStatTypeProviderImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RFile;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyElementFactory;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod;
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo;
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract;
import org.jetbrains.ruby.codeInsight.types.signature.SignatureNode;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AddContractAnnotationIntention extends BaseIntentionAction {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "Add type contract";
    }

    @NotNull
    @Override
    public String getText() {
        return "Add type contract";
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = file.findElementAt(offset);
        final RMethod method = PsiTreeUtil.getParentOfType(element, RMethod.class);
        if (!file.isWritable()) {
            return false;
        }
        if (method == null) {
            return false;
        }

        final MethodInfo methodInfo = createMethodInfo(method);
        if (methodInfo == null) {
            return false;
        }
        final SignatureContract contract = SignatureServer.INSTANCE.getContract(methodInfo);
        return contract != null && getContractAsStringList(contract) != null;
    }

    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        final RMethod method = PsiTreeUtil.getParentOfType(element, RMethod.class);
        assert method != null : "Method cannot be null here";
        final MethodInfo methodInfo = createMethodInfo(method);
        assert methodInfo != null : "MethodInfo mustn't be null: it was checked in isAvailable";
        final SignatureContract contract = SignatureServer.INSTANCE.getContract(methodInfo);
        final RFile fileWithComments = RubyElementFactory.createRubyFile(project, getContractAsStringList(contract).stream().reduce("# @contract", (s, s2) -> s + "\n# " + s2));
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
        return RubyStatTypeProviderImpl.findMethodInfo(methodName,
                receiverName.isEmpty() ? CoreTypes.Object : receiverName,
                module,
                SignatureServer.INSTANCE.getStorage());
    }

    @Nullable
    private List<String> getContractAsStringList(SignatureContract contract) {
        final List<String> contracts = new ArrayList<>();
        final SignatureNode startNode = contract.getStartNode();
        dfs(startNode, new StringBuilder(), contracts);
        if (contracts.size() > 3) {
            return null;
        }

        return contracts.stream().map(s -> {
            final int i = s.lastIndexOf(",");
            if (i <= 0) {
                return "-> " + s;
            } else {
                return "(" + s.substring(0, i) + ") -> " + s.substring(i + 2);
            }
        }).collect(Collectors.toList());
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

}