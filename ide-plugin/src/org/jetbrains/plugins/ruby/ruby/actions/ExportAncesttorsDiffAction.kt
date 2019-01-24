package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorByObjectSpace
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorByRubyMine
import org.jetbrains.plugins.ruby.ancestorsextractor.RubyModule
import java.io.PrintWriter

class ExportAncestorsDiffAction : ExportFileActionBase(whatToExport = "ancestors diff",
        defaultFileName = "ancestors-diff", extensions = arrayOf("txt"),
        numberOfProgressBarFractions = 9) {
    override fun backgroundProcess(absoluteFilePath: String, module: Module?, sdk: Sdk?, project: Project) {
        moveProgressBarForward()
        val byObjectSpace: List<RubyModule>
        val byRubyMine: List<RubyModule>
        val ancestorHashSymbolIncluderToWhereIncluded: Map<String, String>
        try {
            val listener = ProgressListener()
            val ancestorsExtractorByObjectSpace = AncestorsExtractorByObjectSpace(listener)

            // Here all listener methods would be called
            byObjectSpace = ancestorsExtractorByObjectSpace.extractAncestors(project, sdk ?: throw IllegalStateException("Ruby SDK is not set"))

            // The second place where all listener methods would be called
            ancestorHashSymbolIncluderToWhereIncluded = ancestorsExtractorByObjectSpace.extractIncludes(project, sdk)

            // Provide all modulesNames same as in byObjectSpace for easy ancestors comparison
            val allModulesNames: List<String> = byObjectSpace.map { it.name }

            // The third place where all listener methods would be called
            byRubyMine = AncestorsExtractorByRubyMine(allModulesNames, listener)
                    .extractAncestors(project, sdk)
        } catch (ex: Throwable) {
            PrintWriter(absoluteFilePath).use {
                it.println(ex.message)
            }
            return
        }

        moveProgressBarForward()
        PrintWriter(absoluteFilePath).use { printWriter ->
            val objectSpaceIterator = byObjectSpace.iterator()
            val rubymineIterator = byRubyMine.iterator()
            while (objectSpaceIterator.hasNext() && rubymineIterator.hasNext()) {
                val a = objectSpaceIterator.next()
                val b = rubymineIterator.next()
                assert(a.name == b.name)
                printWriter.println("Module: ${a.name}")
                var same = true
                a.ancestors.filter { !b.ancestors.contains(it) }.let {
                    if (!it.isEmpty()) {
                        same = false
                        printWriter.print("Ancestors in ObjectSpace only: ")
                        it.forEach {
                            val whereIncluded = ancestorHashSymbolIncluderToWhereIncluded[it + "#" + a.name]
                            var toPrint = it
                            if (whereIncluded != null) {
                                toPrint += "($whereIncluded)"
                            }
                            printWriter.print("$toPrint ")
                        }
                        printWriter.println()
                    }
                }
                b.ancestors.filter { !a.ancestors.contains(it) }.let {
                    if (!it.isEmpty()) {
                        same = false
                        printWriter.print("Ancestors in RubyMine only: ")
                        it.forEach { printWriter.print("$it ") }
                        printWriter.println()
                    }
                }
                if (same) {
                    printWriter.println("No difference in ancestors list")
                }
                printWriter.println()
            }
        }
        moveProgressBarForward()
    }
}