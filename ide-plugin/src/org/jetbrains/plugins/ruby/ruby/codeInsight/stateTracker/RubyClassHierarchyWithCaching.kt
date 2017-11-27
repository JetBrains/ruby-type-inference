package org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Types
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSyntheticSymbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil
import org.jetbrains.plugins.ruby.ruby.persistent.TypeInferenceDirectory
import org.jetbrains.plugins.ruby.settings.RubyTypeContractsSettings
import org.jetbrains.ruby.stateTracker.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class RubyClassHierarchyWithCaching private constructor(private val rubyClassHierachy: RubyClassHierarchy) {
    private val lookupCache = ContainerUtil.createConcurrentWeakMap<Pair<String, String>, CachedValue>()
    private val membersCache = ContainerUtil.createConcurrentWeakMap<String, Set<Symbol>>()

    fun getTypeForConstant(constant: String): RubyConstant? {
        return rubyClassHierachy.topLevelConstants[constant]
    }

    fun getMembersWithCaching(moduleName: String, topLevel: Symbol) : Set<Symbol> {
        val module = rubyClassHierachy.getRubyModule(moduleName) ?: return emptySet()
        return getMembersWithCaching(module, topLevel)
    }

    private fun lookupInstanceMethodWithCaching(module: RubyModule, methodName: String) : RubyMethod? {
        val pair = Pair(module.name, methodName)
        return lookupCache.computeIfAbsent(pair) { CachedValue(lookupInstanceMethod(module, methodName)) }.rubyMethod
    }

    private fun lookupInstanceMethod(module: RubyModule, methodName: String): RubyMethod? {
        val ownResult = module.instanceMethods.firstOrNull {it.name == methodName}
        if (ownResult != null) {
            return ownResult
        }

        module.instanceIncluded.forEach {
            val result = lookupInstanceMethodWithCaching(it, methodName)
            if (result != null) {
                return result
            }
        }

        if (module is RubyClass && module.superClass != RubyClass.EMPTY) {
            val result = lookupInstanceMethodWithCaching(module.superClass, methodName)
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun getMembersWithCaching(module: RubyModule, topLevel: Symbol) : Set<Symbol> {
        return membersCache.computeIfAbsent(module.name) { getMembers(module, topLevel) }
    }

    private fun getMembers(module: RubyModule, topLevel: Symbol) : Set<Symbol> {
        val set = HashSet<Symbol>()
        val symbol = SymbolUtil.findSymbol(topLevel, FQN.Builder.fromString(module.name), Types.MODULE_OR_CLASS, null)
        set.addAll(module.instanceMethods.map {  RMethodSyntheticSymbol(topLevel.project, Type.INSTANCE_METHOD, it, symbol) })
        set.addAll(module.classMethods.map {  RMethodSyntheticSymbol(topLevel.project, Type.CLASS_METHOD, it, symbol) })

        module.instanceIncluded.forEach { set.addAll(getMembersWithCaching(it, topLevel))}
        module.classIncluded.forEach{ set.addAll(getMembersWithCaching(it, topLevel)) }

        if (module is RubyClass && module.superClass != RubyClass.EMPTY) {
            set.addAll(getMembersWithCaching(module.superClass, topLevel))
        }

        return set
    }

    data class CachedValue(val rubyMethod: RubyMethod?)

    companion object {
        private val CLASS_HIERARCHY_KEY = Key<RubyClassHierarchyWithCaching>("org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.ClassHierarchy")

        private val CLASS_HIERARCHY_FILENAME = "-class-hierarchy.json.gz"

        fun loadFromSystemDirectory(module: Module): RubyClassHierarchyWithCaching? {
            val file = File(TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toFile(),
                    module.project.name + "-" + module.name + CLASS_HIERARCHY_FILENAME)
            if (!file.exists()) {
                return null
            }
            FileInputStream(file).use {
                GZIPInputStream(it).use {
                    val json = it.reader(Charsets.UTF_8).use{ it.readText() }
                    return createClassHierarchyFromJson(json, module)
                }
            }
        }

        @Synchronized
        fun updateAndSaveToSystemDirectory(jsons: List<String>, module: Module) {
            val json = RubyClassHierarchyLoader.mergeJsons(jsons)
            createClassHierarchyFromJson(json, module)
            FileOutputStream(File(TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toFile(),
                    module.project.name + "-" + module.name + CLASS_HIERARCHY_FILENAME)).use {
                GZIPOutputStream(it).use {
                    it.writer(Charsets.UTF_8).use { it.write(json) }
                }
            }
        }

        private fun createClassHierarchyFromJson(json: String, module: Module) : RubyClassHierarchyWithCaching {
            val rubyClassHierarchy = RubyClassHierarchyWithCaching(RubyClassHierarchyLoader.fromJson(json))
            module.putUserData(RubyClassHierarchyWithCaching.CLASS_HIERARCHY_KEY,
                    rubyClassHierarchy)
            return rubyClassHierarchy

        }

        fun getInstance(module: Module): RubyClassHierarchyWithCaching? {
            if (!ServiceManager.getService(module.project, RubyTypeContractsSettings::class.java).stateTrackerEnabled) {
                return null
            }
            val ret = module.getUserData(CLASS_HIERARCHY_KEY)
            if (ret != null) {
                return ret
            }
            return null
        }
    }
}