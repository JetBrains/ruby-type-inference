package org.jetbrains.plugins.ruby.ruby.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.registeredCallInfosCache
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RFName
import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable

class RemoveCollectedInfoIntention : BaseRubyMethodIntentionAction() {
    override fun getFamilyName(): String = getText()

    override fun getTextByRubyFunctionNamePsiElement(element: RFName?): String {
        return "Remove collected info about ${element?.name ?: "this"} method"
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val method = getRFName(editor, file)?.let { RubyPsiUtil.getContainingRMethod(it) } ?: return
        val rubyModuleName = RubyPsiUtil.getContainingRClassOrModule(method)?.fqn?.fullPath ?: "Object"

        val info = MethodInfo.Impl(ClassInfo.Impl(null, rubyModuleName), method.fqn.shortName)

        transaction { CallInfoTable.deleteAllInfoRelatedTo(info) }

        registeredCallInfosCache.clear()
    }
}
