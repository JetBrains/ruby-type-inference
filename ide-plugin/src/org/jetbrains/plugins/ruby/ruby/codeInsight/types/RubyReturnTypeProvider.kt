package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.ruby.ruby.codeInsight.AbstractRubyTypeProvider
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.RubyClassHierarchyWithCaching
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSymbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.settings.RubyTypeContractsSettings
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths

class RubyReturnTypeProvider : AbstractRubyTypeProvider() {

    override fun createTypeBySymbol(p0: Symbol?, p1: Context?): RType? {
        return null
    }

    override fun createTypeByRExpression(expr: RExpression): RType? {
        val symbol = ResolveUtil.resolveToSymbolWithCaching(expr.reference, false)
        if (symbol is RMethodSymbol) {
            val rubyReturnTypeData = getInstance(expr.project) ?: return null
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
        return null
    }

    companion object {
        private val KEY = Key<RubyReturnTypeData>("org.jetbrains.plugins.ruby.ruby.codeInsight.types.RubyReturnTypeData")
        private val RUBY_TYPE_INFERENCE_DIRECTORY = Paths.get(System.getProperty("idea.system.path"), "ruby-type-inference")

        fun loadJson(project: Project) {
            val result = tryLoadJson()
            if (result != null) {
                project.putUserData(KEY, result)
            }
        }

        private fun tryLoadJson(): RubyReturnTypeData? {
            val file = File(RUBY_TYPE_INFERENCE_DIRECTORY.toFile(), "calls.json")
            if (!file.exists()) {
                return null
            }
            FileInputStream(file).use {
                val json = it.reader(Charsets.UTF_8).use{ it.readText() }
                return RubyReturnTypeData.createFromJson(json)
            }

        }

        private fun getInstance(project: Project): RubyReturnTypeData? {
            if (!ServiceManager.getService(project, RubyTypeContractsSettings::class.java).stateTrackerEnabled) {
                return null
            }
            return project.getUserData(KEY)
        }
    }

}