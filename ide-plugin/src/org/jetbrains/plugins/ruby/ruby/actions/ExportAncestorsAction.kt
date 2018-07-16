package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractor
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorByObjectSpace
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorByRubyMine
import org.jetbrains.plugins.ruby.ancestorsextractor.RubyModule
import java.io.PrintWriter

open class ExportAncestorsAction(
        whatToExport: String,
        defaultFileName: String,
        private val extractor: AncestorsExtractor
) : ExportFileAction(whatToExport, defaultFileName, arrayOf("txt")) {
    override fun backgroundProcess(project: Project, absoluteFilePath: String) {
        val ancestors: List<RubyModule> = extractor.extractAncestors(project) ?: return
        PrintWriter(absoluteFilePath).use { printWriter ->
            ancestors.forEach {
                printWriter.println("Module: ${it.name}")
                printWriter.print("Ancestors: ")
                if (it.ancestors.isEmpty()) printWriter.print("Nothing found")
                it.ancestors.forEach { printWriter.print("$it ") }
                printWriter.print("\n\n")
            }
        }
    }
}

class ExportAncestorsByObjectSpaceAction : ExportAncestorsAction(
        whatToExport = "ancestors by Objectspace",
        defaultFileName = "ancestors-by-objectspace",
        extractor = AncestorsExtractorByObjectSpace()
)

class ExportAncestorsByRubymineAction : ExportAncestorsAction(
        whatToExport = "ancestors by Rubymine",
        defaultFileName = "ancestors-by-rubymine",
        extractor = AncestorsExtractorByRubyMine()
)