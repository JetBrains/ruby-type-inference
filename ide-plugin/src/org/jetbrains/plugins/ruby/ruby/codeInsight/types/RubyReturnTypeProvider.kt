package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.intellij.openapi.module.ModuleUtilCore
import org.jetbrains.plugins.ruby.ruby.codeInsight.AbstractRubyTypeProvider
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.scope.RElementWithFQN
import org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.RubyClassHierarchyWithCaching
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSymbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modules.RModule
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier
import org.jetbrains.ruby.codeInsight.types.signature.CallInfo
import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.RVisibility
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl

class RubyReturnTypeProvider : AbstractRubyTypeProvider() {

    override fun createTypeBySymbol(p0: Symbol?, p1: Context?): RType? {
        return null
    }

    override fun createTypeByRExpression(expr: RExpression): RType? {
        val symbol = ResolveUtil.resolveToSymbolWithCaching(expr.reference, false)
        if (symbol is RMethodSymbol) {
            val module = ModuleUtilCore.findModuleForPsiElement(expr) ?: return null
            val rubyReturnTypeData = RubyReturnTypeData.getInstance(module) ?: return null
            val parent = symbol.parentSymbol ?: return null
            val name = symbol.name ?: return null
            val result = rubyReturnTypeData.getTypeByFQNAndMethodName(parent.fqnWithNesting.fullPath, name) ?: return null
            return result.map { RTypeFactory.createTypeByFQN(expr.project, it) }.reduce { t, n -> RTypeUtil.union(t, n) }
        }
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
            val method = expr.parentContainer as? RMethod ?: return null
            val moduleName = (method.parentContainer as? RElementWithFQN)?.fqn?.fullPath ?: return null
            val indexOfArgument = method.arguments.indexOfFirst { it.identifier == expr }

            val info = MethodInfo(ClassInfo(moduleName), method.fqn.shortName, RVisibility.PUBLIC)

            val callInfos: List<CallInfo> = RSignatureProviderImpl().getRegisteredCallInfos(info)

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

private fun List<RType>.unionTypes(startIndex: Int = 0, endIndex: Int = size - 1): RType {
    if (endIndex < startIndex) {
        return REmptyType.INSTANCE
    }

    if (startIndex == endIndex) {
        return this[startIndex]
    }
    val m = (startIndex + endIndex) / 2
    return RTypeUtil.union(this.unionTypes(startIndex, m), this.unionTypes(m + 1, endIndex))
}