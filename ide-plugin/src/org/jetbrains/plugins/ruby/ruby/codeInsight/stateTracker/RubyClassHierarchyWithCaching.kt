package org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker

import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Types
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSyntheticSymbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil
import org.jetbrains.ruby.stateTracker.RubyClass
import org.jetbrains.ruby.stateTracker.RubyClassHierarchy
import org.jetbrains.ruby.stateTracker.RubyMethod
import org.jetbrains.ruby.stateTracker.RubyModule
import java.util.concurrent.ConcurrentMap


class RubyClassHierarchyWithCaching(private val rubyClassHierachy: RubyClassHierarchy) {
    private val lookupCache = ContainerUtil.createConcurrentWeakMap<Pair<String, String>, CachedValue>()
    private val membersCache = ContainerUtil.createConcurrentWeakMap<String, Set<Symbol>>()

    fun lookupInstanceMethodWithCaching(moduleName: String, methodName: String) : RubyMethod? {
        val module = rubyClassHierachy.getRubyModule(moduleName) ?: return null
        return lookupInstanceMethodWithCaching(module, methodName)
    }

    fun getMembersWithCaching(moduleName: String, topLevel: Symbol) : Set<Symbol> {
        val module = rubyClassHierachy.getRubyModule(moduleName) ?: return emptySet()
        return getMembersWithCaching(module, topLevel)
    }

    private fun lookupInstanceMethodWithCaching(module: RubyModule, methodName: String) : RubyMethod? {
        val pair = Pair(module.name, methodName)
        val cached = lookupCache[pair]
        return if (cached == null) {
            val result = lookupInstanceMethod(module, methodName)
            updateCache(pair, CachedValue(result), lookupCache)
            result
        } else {
            cached.rubyMethod
        }
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
        val cached = membersCache[module.name]
        return if (cached != null) {
            cached
        } else {
            val result = getMembers(module, topLevel)
            updateCache(module.name, result, membersCache)
            result
        }
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

    private fun <K, V> updateCache(key: K, result: V, cache: ConcurrentMap<K, V>) {
        val cached = cache[key]
        if (cached === result) {
            return
        }
        cache.put(key, result)
    }



    data class CachedValue(val rubyMethod: RubyMethod?)

    companion object {
        val KEY = Key<RubyClassHierarchyWithCaching>("org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.TypeHierarchy")
    }
}