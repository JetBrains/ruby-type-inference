package org.jetbrains.plugins.ruby.ruby.persistent

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

object TypeInferenceDirectory {
    val RUBY_TYPE_INFERENCE_DIRECTORY by lazy {
        val path = Paths.get(System.getProperty("idea.system.path"), "ruby-type-inference")
        FileUtil.createDirectory(path.toFile())
        path
    }
}