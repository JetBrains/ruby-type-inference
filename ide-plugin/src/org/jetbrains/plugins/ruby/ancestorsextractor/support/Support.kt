import java.io.File
import java.io.IOException
import java.io.PrintWriter

fun runRailsConsole(projectDirPath: String, toExec: String) {
    val projectDir = File(projectDirPath)
    try {
        val proc = ProcessBuilder("/bin/bash", "-ic", "bin/rails console")
                .directory(projectDir)
                .start()

        PrintWriter(proc.outputStream, true).use { it.println(toExec) }
        // free buffers
        proc.inputStream.bufferedReader().readText()
        proc.errorStream.bufferedReader().readText()

        proc.waitFor()
    } catch(e: IOException) {
        e.printStackTrace()
    }
}

class TempFilePathProvider(private val init: () -> String) {
    private var _path: String? = null
    val path: String
        get() {
            val pathLocal = _path ?: init()
            _path = pathLocal
            return pathLocal
        }

    fun removeTempFileIfExistsAndForgetAboutIt() {
        val pathLocal = _path
        if (pathLocal != null) {
            File(pathLocal).delete()
            _path = null
        }
    }
}

object TempFiles {
    val tempFilePathProviderForModules = TempFilePathProvider {
        "/tmp/modules${System.currentTimeMillis()}.json"
    }
    val tempFilePathProviderForModuleAncestorsPair = TempFilePathProvider {
        "/tmp/module-ancestors-pair${System.currentTimeMillis()}.json"
    }
}