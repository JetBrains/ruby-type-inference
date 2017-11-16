package org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker

import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.ruby.stateTracker.RubyClass
import org.jetbrains.ruby.stateTracker.RubyClassHierarchy
import org.jetbrains.ruby.stateTracker.RubyMethod
import org.jetbrains.ruby.stateTracker.RubyModule

class RubyClassHierarchyWithCaching(private val rubyClassHierachy: RubyClassHierarchy) {
    private val cache = ContainerUtil.createConcurrentWeakMap<Pair<String, String>, CachedValue>()

    fun lookupInstanceMethodWithCaching(moduleName: String, methodName: String) : RubyMethod? {
        val module = rubyClassHierachy.getRubyModule(moduleName) ?: return null
        return lookupInstanceMethodWithCaching(module, methodName)
    }

    private fun lookupInstanceMethodWithCaching(module: RubyModule, methodName: String) : RubyMethod? {
        val pair = Pair(module.name, methodName)
        val cached = cache[pair]
        return if (cached == null) {
            val result = lookupInstanceMethod(module, methodName)
            cacheResult(pair, CachedValue(result))
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

    private fun cacheResult(key: Pair<String, String>, result: CachedValue) {
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