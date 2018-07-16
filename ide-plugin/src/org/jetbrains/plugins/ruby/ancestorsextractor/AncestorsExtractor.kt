package org.jetbrains.plugins.ruby.ancestorsextractor

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.util.SymbolScopeUtil
import runRailsConsole
import java.io.File
import java.io.IOException

data class RubyModule(val name: String, val ancestors: List<String>)

interface AncestorsExtractor {
    fun extractAncestors(project: Project): List<RubyModule>?
}

class AncestorsExtractorByRubyMine(private var allModulesNames: List<String>? = null) : AncestorsExtractor {
    override fun extractAncestors(project: Project): List<RubyModule>? {
        val allModulesNamesLocal: List<String>? = allModulesNames
                ?: ModulesNamesProviderObjectSpaceImpl().getAllModulesNames(project)
        allModulesNames = allModulesNamesLocal
        return allModulesNamesLocal?.map { RubyModule(it, extractAncestorsFor(it, project)) }
    }

    private fun extractAncestorsFor(rubyModuleName: String, project: Project): List<String> {
        return SymbolUtil.findConstantByFQN(project, rubyModuleName)?.let {
            SymbolScopeUtil.getAncestorsCaching(it, null)
        }?.map { it.symbol.fqnWithNesting.toString() } ?: listOf()
    }
}

class AncestorsExtractorByObjectSpace : AncestorsExtractor {
    private val gson = Gson()

    override fun extractAncestors(project: Project): List<RubyModule>? {
        val projectDirPath = project.basePath ?: return null

        runRailsConsole(projectDirPath,
            """
                objects = ObjectSpace.each_object(Module).to_a.map {|mod| {:name => mod.to_s, :ancestors => mod.ancestors.map {|from| from.to_s}}}
                require 'json'
                open("${TempFiles.tempFilePathProviderForModuleAncestorsPair.path}", "w") do |f|
                  f.puts JSON.generate(objects)
                end
            """.trimIndent()
        )

        val file = File(TempFiles.tempFilePathProviderForModuleAncestorsPair.path)
        return try {
            val a = file.inputStream().bufferedReader().use {
                gson.fromJson(it.readLine(), Array<RubyModule>::class.java)
            }?.toList()
            a
        } catch (ex: IOException) {
            null
        } finally {
            TempFiles.tempFilePathProviderForModuleAncestorsPair.removeTempFileIfExistsAndForgetAboutIt()
        }
    }
}
