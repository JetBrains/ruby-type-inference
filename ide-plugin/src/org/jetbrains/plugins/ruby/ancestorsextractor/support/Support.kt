import java.io.File
import java.io.IOException
import java.io.PrintWriter

/**
 * Run [toExec] in ruby on rails console. You can use it for example for generating
 * some temp json files to later parse them in Kotlin/Java
 * @param projectDirPath Path to project dir
 * @param toExec Newline separated [String] to execute in ruby on rails console
 */
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

/**
 * Provides temp file paths
 * @param initTempFileName lambda where you can generate temp file name
 */
class TempFilePathProvider(private val initTempFileName: () -> String) {
    private var _path: String? = null
    /**
     * Path to temp file.
     * @see removeTempFileIfExistsAndForgetAboutIt
     */
    val path: String
        get() {
            val pathLocal = _path ?: initTempFileName()
            _path = pathLocal
            return pathLocal
        }

    /**
     * Remove temp file if exists. And forget about that temp file path, that means that next [path] call
     * will call [initTempFileName] once more to get new temp file path and since that moment will return
     * it until next [removeTempFileIfExistsAndForgetAboutIt] call.
     */
    fun removeTempFileIfExistsAndForgetAboutIt() {
        val pathLocal = _path
        if (pathLocal != null) {
            File(pathLocal).delete()
            _path = null
        }
    }
}

/**
 * Singleton which keeps all temp files paths
 */
object TempFiles {
    val tempFilePathProviderForModules = TempFilePathProvider {
        "/tmp/modules${System.currentTimeMillis()}.json"
    }
    val tempFilePathProviderForModuleAncestorsPair = TempFilePathProvider {
        "/tmp/module-ancestors-pair${System.currentTimeMillis()}.json"
    }
}