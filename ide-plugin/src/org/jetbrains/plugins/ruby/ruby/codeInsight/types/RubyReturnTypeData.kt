package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.google.gson.Gson
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.ruby.ruby.persistent.TypeInferenceDirectory
import org.jetbrains.plugins.ruby.settings.RubyTypeContractsSettings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

interface RubyReturnTypeData {
    fun getTypeByFQNAndMethodName(fqn: String, name: String) : List<String>?

    private data class Schema(var def : String, var name : String, var ret: String)

    private class Impl(calls: Array<Schema>) : RubyReturnTypeData {
        private val method2Types: HashMap<Pair<String, String>, out List<String>>
        init {
            val result = HashMap<Pair<String, String>, MutableList<String>>()
            calls.forEach {
                val pair = Pair(it.def, it.name)
                result.computeIfAbsent(pair, { ArrayList() }).add(it.ret)
            }
            method2Types = result
        }

        override fun getTypeByFQNAndMethodName(fqn: String, name: String): List<String>? {
            return method2Types[Pair(fqn, name)]
        }
    }

    companion object {
        private val gson = Gson()

        private val KEY = Key<RubyReturnTypeData>("org.jetbrains.plugins.ruby.ruby.codeInsight.types.RubyReturnTypeData")
        private val RETURN_TYPE_FILENAME="-calls.json.gz"

        fun getInstance(module: Module): RubyReturnTypeData? {
            if (!ServiceManager.getService(module.project, RubyTypeContractsSettings::class.java).stateTrackerEnabled) {
                return null
            }
            return module.getUserData(KEY)
        }

        fun loadJson(module: Module) {
            val result = tryLoadJson(module)
            if (result != null) {
                module.putUserData(KEY, result)
            }
        }

        @Synchronized
        fun updateAndSaveToSystemDirectory(jsons: List<String>, module: Module) {
            val json = joinJsons(jsons)
            val file = File(TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toFile(),
                    module.project.name + "-" + module.name + RETURN_TYPE_FILENAME)
            FileOutputStream(file).use {
                GZIPOutputStream(it).use {
                    it.writer(Charsets.UTF_8).use { it.write(json) }
                    module.putUserData(KEY, RubyReturnTypeData.createFromJson(json))
                }
            }
        }

        private fun joinJsons(jsons: List<String>): String {
            return gson.toJson(jsons.map{gson.fromJson(it, Array<Schema>::class.java).toList()}.
                    reduce {a, b -> a.union(b).toList()})
        }

        private fun tryLoadJson(module: Module): RubyReturnTypeData? {
            val file = File(TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toFile(),
                    module.project.name + "-" + module.name + RETURN_TYPE_FILENAME)
            if (!file.exists()) {
                return null
            }
            FileInputStream(file).use {
                GZIPInputStream(it).use {
                    val json = it.reader(Charsets.UTF_8).use { it.readText() }
                    return RubyReturnTypeData.createFromJson(json)
                }
            }
        }

        private fun createFromJson(json: String) : RubyReturnTypeData {
            return Impl(gson.fromJson(json, Array<Schema>::class.java))
        }
    }
}


