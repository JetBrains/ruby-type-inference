package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.ruby.ruby.codeInsight.AbstractRubyTypeProvider
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.RubyClassHierarchyWithCaching
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSymbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl

class RubyReturnTypeProvider : AbstractRubyTypeProvider() {

    override fun createTypeBySymbol(p0: Symbol?, p1: Context?): RType? {
        return null
    }

    override fun createTypeByRExpression(expr: RExpression): RType? {
        val symbol = ResolveUtil.resolveToSymbolWithCaching(expr.reference, false)
        if (symbol?.type == Type.CONSTANT) {
            val module = ModuleUtilCore.findModuleForPsiElement(expr) ?: return null
            val classHierarchyWithCaching = RubyClassHierarchyWithCaching.getInstance(module) ?: return null
            val path = symbol?.fqnWithNesting?.fullPath ?: return null
            val constant = classHierarchyWithCaching.getTypeForConstant(path) ?: return null
            val originType = RTypeFactory.createTypeByFQN(expr.project, constant.type)
            val mixins = constant.extended.map { RTypeFactory.createTypeByFQN(expr.project, it) }
            if (mixins.isNotEmpty()) {
                return RTypeUtil.union(originType,  mixins.reduce { acc, it -> RTypeUtil.union(acc, it) })
            }
            return originType
        }
        if (expr is RIdentifier && expr.isParameter) {
            val method = RubyPsiUtil.getContainingRMethod(expr) ?: return null
            val methodLineNumber = PsiUtilBase.findEditor(method)?.document?.getLineNumber(method.textOffset)?.plus(1)
            val rubyModuleName = RubyPsiUtil.getContainingRClassOrModule(method)?.fqn?.fullPath ?: "Object"
            val numberOfArgs = method.arguments.size
            val indexOfArgument = method.arguments.indexOfFirst { it.identifier == expr }

            val info = MethodInfo.Impl(ClassInfo(rubyModuleName), method.fqn.shortName, RVisibility.PUBLIC,
                    methodLineNumber?.let { Location(method.containingFile.virtualFile.path, methodLineNumber) })

            val callInfos: List<CallInfo> = RSignatureProviderImpl().getRegisteredCallInfos(info, numberOfArgs)

            return callInfos.map {
                if (indexOfArgument in 0 until it.argumentsTypes.size) {
                    RTypeFactory.createTypeClassName(it.argumentsTypes[indexOfArgument], expr)
                } else {
                    REmptyType.INSTANCE
                }
            }.filter { it != REmptyType.INSTANCE }.distinctBy { it.name }.unionTypes()
        }
        return null
    }
}

private fun List<RType>.unionTypes(): RType {
    if (size == 0) {
        return REmptyType.INSTANCE
    }

    if (size == 1) {
        return first()
    }
    val m = size / 2
    return RTypeUtil.union(subList(0, m).unionTypes(), subList(m, size).unionTypes())
}
