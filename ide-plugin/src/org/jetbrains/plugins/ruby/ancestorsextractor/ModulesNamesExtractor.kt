package org.jetbrains.plugins.ruby.ancestorsextractor

import TempFiles
import com.google.gson.Gson
import com.intellij.openapi.project.Project
import runRailsConsole
import java.io.File
import java.io.IOException

/**
 * Extract all modules names from Ruby on Rails project.
 */
interface ModulesNamesExtractor {
    /**
     * Extract all modules names from Ruby on Rails project.
     */
    fun extractAllModulesNames(project: Project): List<String>?
}

/**
 * Implementation based on Ruby's ObjectSpace.each_object function.
 * @see <a href="https://ruby-doc.org/core-2.2.0/ObjectSpace.html#method-c-each_object">ObjectSpace's each_object function</a>
 */
class ModulesNamesExtractorObjectSpaceImpl : ModulesNamesExtractor {
    private val gson = Gson()

    /**
     * Implementation of [ModulesNamesExtractor.extractAllModulesNames] based on Ruby's ObjectSpace.each_object function.
     * @see <a href="https://ruby-doc.org/core-2.2.0/ObjectSpace.html#method-c-each_object">ObjectSpace's each_object function</a>
     */
    override fun extractAllModulesNames(project: Project): List<String>? {
        val basePath = project.basePath ?: return null

        runRailsConsole(basePath,
            """
                require 'json'
                open("${TempFiles.tempFilePathProviderForModules.path}", "w") do |f|
                  f.puts JSON.generate(ObjectSpace.each_object(Module).to_a.map {|from| from.to_s})
                end
            """.trimIndent()
        )

        val file = File(TempFiles.tempFilePathProviderForModules.path)
        return try {
            file.inputStream().bufferedReader().use {
                gson.fromJson(it.readLine(), Array<String>::class.java)
            }?.toList()
        } catch (ex: IOException) {
            null
        } finally {
            TempFiles.tempFilePathProviderForModules.removeTempFileIfExistsAndForgetAboutIt()
        }
    }
}