package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.ruby.ruby.codeInsight.AbstractRubyTypeProvider
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil
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
        val methodSymbol = ResolveUtil.resolveToSymbolWithCaching(expr.reference, false)
        val rubyReturnTypeData = getInstance(expr.project) ?: return null
        if (methodSymbol is RMethodSymbol) {
            val parent = methodSymbol.parentSymbol ?: return null
            val name = methodSymbol.name ?: return null
            val result = rubyReturnTypeData.getTypeByFQNAndMethodName(parent.fqnWithNesting.fullPath, name) ?: return null
            val retType = result.map { RTypeFactory.createTypeByFQN(expr.project, it) }.reduce { t, n -> RTypeUtil.union(t, n) }
            return retType
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