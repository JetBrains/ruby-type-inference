package org.jetbrains.plugins.ruby.ancestorsextractor

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.util.SymbolScopeUtil
import runRailsConsole
import java.io.File
import java.io.IOException

/**
 * Keeps Ruby module [name] and it's [ancestors]
 */
data class RubyModule(val name: String, val ancestors: List<String>)

/**
 * Ancestors extractor for Ruby's project's modules
 */
interface AncestorsExtractor {
    /**
     * Extract ancestors for every Ruby's Module in [project]
     */
    fun extractAncestors(project: Project): List<RubyModule>?
}

/**
 * Extract ancestors for Ruby's modules the way how Ruby Mine sees them.
 * If you don't provide [allModulesNames] this implementation works only for Ruby on Rails project as in case when
 * you don't provide [allModulesNames] all modules names would be taken from
 * [ModulesNamesExtractorObjectSpaceImpl] which works only for Ruby on Rails project
 */
class AncestorsExtractorByRubyMine(private var allModulesNames: List<String>? = null) : AncestorsExtractor {
    /**
     * Implementation of [AncestorsExtractor.extractAncestors] based on how Ruby Mine sees ancestors
     */
    override fun extractAncestors(project: Project): List<RubyModule>? {
        val allModulesNamesLocal: List<String>? = allModulesNames
                ?: ModulesNamesExtractorObjectSpaceImpl().extractAllModulesNames(project)
        allModulesNames = allModulesNamesLocal
        return allModulesNamesLocal?.map { RubyModule(it, extractAncestorsFor(it, project)) }
    }

    private fun extractAncestorsFor(rubyModuleName: String, project: Project): List<String> {
        return SymbolUtil.findConstantByFQN(project, rubyModuleName)?.let {
            SymbolScopeUtil.getAncestorsCaching(it, null)
        }?.map { it.symbol.fqnWithNesting.toString() } ?: listOf()
    }
}

/**
 * Extract ancestors the way Ruby's Module.ancestors method does this.
 * This implementation works only for Ruby on Rails project.
 * @see <a href="https://ruby-doc.org/core-2.1.0/Module.html#method-i-ancestors">Ruby's Module.ancestors</a>
 */
class AncestorsExtractorByObjectSpace : AncestorsExtractor {
    private val gson = Gson()

    /**
     * Implementation of [AncestorsExtractor.extractAncestors] based on Ruby's Module.ancestors method
     * @see <a href="https://ruby-doc.org/core-2.1.0/Module.html#method-i-ancestors">Ruby's Module.ancestors</a>
     */
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
