package org.jetbrains.plugins.ruby.ancestorsextractor

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.util.SymbolScopeUtil
import java.io.FileWriter
import java.io.PrintWriter

/**
 * Keeps Ruby module [name] and it's [ancestors]
 */
data class RubyModule(val name: String, val ancestors: List<String>)

/**
 * Ancestors extractor for Ruby's project's modules
 */
interface AncestorsExtractorBase {
    /**
     * Extract ancestors for every Ruby's Module in [project]
     */
    fun extractAncestors(project: Project, sdk: Sdk): List<RubyModule>

    /**
     * Set [RailsConsoleRunner.Listener] for [RailsConsoleRunner]
     * @see RailsConsoleRunner.Listener
     */
    var listener: RailsConsoleRunner.Listener?
}

/**
 * Extract ancestors for Ruby's modules the way how RubyMine sees them.
 * If you don't provide [allModulesNames] this implementation works only for Ruby on Rails project as in case when
 * you don't provide [allModulesNames] all modules names would be taken from Ruby on Rails console ("bin/rails console")
 */
class AncestorsExtractorByRubyMine(private val allModulesNames: List<String>? = null,
                                   override var listener: RailsConsoleRunner.Listener? = null) : AncestorsExtractorBase {
    /**
     * Implementation of [AncestorsExtractorBase.extractAncestors] based on how RubyMine sees ancestors
     */
    override fun extractAncestors(project: Project, sdk: Sdk): List<RubyModule> {
        val allModulesNamesLocal: List<String> = allModulesNames ?: extractAllModulesNames(project, sdk)
        // I don't know why but seems that SymbolScopeUtil#getAncestorsCaching needs to be called
        // inside ReadAction otherwise sometimes I get Exception
        return ReadAction.compute(ThrowableComputable<List<RubyModule>, Exception> {
            allModulesNamesLocal.map { RubyModule(it, extractAncestorsFor(it, project)) }
        })
    }

    /**
     * Extract all modules names from Ruby on Rails project.
     */
    private fun extractAllModulesNames(project: Project, sdk: Sdk): List<String> {
        val tempFile = createTempFile(prefix = "modules", suffix = ".json")
        try {
            val rubyCode = """
                require 'json'
                open("${tempFile.path}", "w") do |f|
                  f.puts JSON.generate(ObjectSpace.each_object(Module).to_a.map {|from| from.to_s})
                end
            """.trimIndent()
            return RailsConsoleRunner(listener).extractFromRubyOnRailsConsole(project, sdk, Array<String>::class.java,
                    tempFile.path, rubyCode, eagerLoad = true).toList()
        } finally {
            tempFile.delete()
        }
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
class AncestorsExtractorByObjectSpace(override var listener: RailsConsoleRunner.Listener? = null) : AncestorsExtractorBase {
    /**
     * Implementation of [AncestorsExtractorBase.extractAncestors] based on Ruby's Module.ancestors method
     * @see <a href="https://ruby-doc.org/core-2.1.0/Module.html#method-i-ancestors">Ruby's Module.ancestors</a>
     */
    override fun extractAncestors(project: Project, sdk: Sdk): List<RubyModule> {
        val tempFile = createTempFile(prefix = "module-ancestors-pair", suffix = ".json")
        try {
            val rubyCode = """
                objects = ObjectSpace.each_object(Module).to_a; nil # nil is to prevent irb to print big objects output
                objects = objects.map {|mod| {:name => mod.to_s, :ancestors => mod.ancestors.map {|from| from.to_s}}}; nil
                require 'json'
                open("${tempFile.path}", "w") do |f|
                  f.puts JSON.generate(objects)
                end
            """.trimIndent()
            return RailsConsoleRunner(listener).extractFromRubyOnRailsConsole(project, sdk, Array<RubyModule>::class.java,
                    tempFile.path, rubyCode, eagerLoad = true).toList()
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Extract information about where ruby includes was performed
     * @return Map where key is [String] with the following format: "**ancestor**#**includer**" and value is [String]
     * containing ruby file path and line number where **ancestor** was included by **includer**
     */
    fun extractIncludes(project: Project, sdk: Sdk): Map<String, String> {
        val tempWhereIncluded = createTempFile(prefix = "where-included", suffix = ".json")
        val tempRubyCodeFile = createTempFile(prefix = "preload-temp-script", suffix = ".rb")
        try {
            PrintWriter(FileWriter(tempRubyCodeFile)).use {
                it.println("""
                    END {
                        require 'json'
                        open("${tempWhereIncluded.path}", "w") { |f| f.puts JSON.generate(RubyDetectIncludeUniqueModuleName.get) }
                    }

                    module RubyDetectIncludeUniqueModuleName
                        @@map = {}
                        def self.get
                            return @@map
                        end

                        def append_features(mod)
                            # self included by mod
                            @@map["#{self.to_s}##{mod.to_s}"] = caller_locations()[1].to_s
                            super
                        end
                    end

                    Module.prepend RubyDetectIncludeUniqueModuleName
                """.trimIndent())
            }
            @Suppress("UNCHECKED_CAST")
            return RailsConsoleRunner(listener).extractFromRubyOnRailsConsole(project, sdk, Map::class.java,
                    tempWhereIncluded.path, rubyCode = "", eagerLoad = true,
                    rubyConsoleArguments = arrayOf("-r", tempRubyCodeFile.path)) as Map<String, String>
        } finally {
            tempRubyCodeFile.delete()
            tempWhereIncluded.delete()
        }
    }
}
