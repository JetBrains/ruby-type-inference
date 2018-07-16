package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorByObjectSpace
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorByRubyMine
import org.jetbrains.plugins.ruby.ancestorsextractor.RubyModule
import java.io.PrintWriter

class ExportAncestorsDiffAction : BaseExportFileAction(whatToExport = "ancestors diff",
        defaultFileName = "ancestors-diff", extensions = arrayOf("txt")) {
    override fun backgroundProcess() {
        val byObjectSpace: List<RubyModule> = AncestorsExtractorByObjectSpace()
                .extractAncestors(project) ?: return

        val allModulesNames = byObjectSpace.map { it.name }

        // Provide all modulesNames same as in byObjectSpace for easy ancestors comparison
        val byRubyMine: List<RubyModule> = AncestorsExtractorByRubyMine(allModulesNames)
                .extractAncestors(project) ?: return

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
                        printWriter.print("In objectspace only: ")
                        it.forEach { printWriter.print("$it ") }
                        printWriter.println()
                    }
                }
                b.ancestors.filter { !a.ancestors.contains(it) }.let {
                    if (!it.isEmpty()) {
                        same = false
                        printWriter.print("In rubymine only: ")
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
    }
}