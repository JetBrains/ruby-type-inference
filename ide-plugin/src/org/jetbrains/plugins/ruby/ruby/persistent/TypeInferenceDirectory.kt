package org.jetbrains.plugins.ruby.ruby.persistent

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

object TypeInferenceDirectory {
    val RUBY_TYPE_INFERENCE_DIRECTORY by lazy {
        val path = Paths.get(PathManager.getSystemPath(), "ruby-type-inference")!!
        FileUtil.createDirectory(path.toFile())
        path
    }
}
