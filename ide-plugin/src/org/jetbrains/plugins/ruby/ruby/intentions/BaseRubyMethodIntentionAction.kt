package org.jetbrains.plugins.ruby.ruby.intentions

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RFName

abstract class BaseRubyMethodIntentionAction : BaseIntentionAction() {
    private var _text: String? = null

    final override fun getText(): String = _text ?: getTextByRubyFunctionNamePsiElement(null)

    protected abstract fun getTextByRubyFunctionNamePsiElement(element: RFName?): String

    protected fun getRFName(editor: Editor, file: PsiFile): RFName? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        return PsiTreeUtil.getParentOfType(element, RFName::class.java)
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val methodNameElement: RFName = getRFName(editor, file) ?: return false
        _text = getTextByRubyFunctionNamePsiElement(methodNameElement)
        return true
    }
}
