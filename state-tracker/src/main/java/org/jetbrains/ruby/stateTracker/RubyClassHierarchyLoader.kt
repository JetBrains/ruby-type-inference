package org.jetbrains.ruby.stateTracker

import com.google.gson.Gson
import java.util.*
import kotlin.collections.ArrayList

object RubyClassHierarchyLoader {

    private val gson = Gson()

    fun fromJson(json : String) : RubyClassHierarchy {
        val root = gson.fromJson(json, Root::class.java)
        return RubyClassHierarchy.Impl(root.load_path,
                                       MapHelper(TopsortHelper(root.modules).topsort()).map())
    }

    private data class Method(var name: String,
                              var path: String?,
                              var line: String?,
                              var parameters: List<List<String>>)

    private data class Module(var name: String,
                              var type: String,
                              var superclass: String,
                              var singleton_class_included: List<String>,
                              var included: List<String>,
                              var class_methods: List<Method>,
                              var instance_methods: List<Method>)

    private data class Root(var load_path: List<String>, var modules: List<Module>)

    private class MapHelper(val topsortedList: List<Module>) {
        private val name2RubyModule = HashMap<String, RubyModule>()
        private val name2Module = topsortedList.associateBy { it.name }

        fun map() : List<RubyModule> = topsortedList.map { toRubyModule(it) }

        private fun toRubyModule(module: Module): RubyModule {
            val rubyModule = when (module.type) {
                "Module" -> RubyModule.Impl(module.name,
                        toRubyModules(minimizeIncluded(module, { m -> m.singleton_class_included })),
                        toRubyModules(minimizeIncluded(module, { m -> m.included })),
                        toRubyMethods(module.class_methods),
                        toRubyMethods(module.instance_methods))
                "Class" -> RubyClass.Impl(module.name,
                        toRubyModules(minimizeIncluded(module, { m -> m.singleton_class_included })),
                        toRubyModules(minimizeIncluded(module, { m -> m.included })),
                        toRubyMethods(module.class_methods),
                        toRubyMethods(module.instance_methods),
                        (name2RubyModule[module.superclass] ?: RubyClass.EMPTY) as RubyClass)
                else -> throw IllegalArgumentException("Unknown module type ${module.type}")
            }
            name2RubyModule[rubyModule.name] = rubyModule
            return rubyModule
        }

        private fun toRubyModules(names: List<String>): List<RubyModule> =
                names.map { name2RubyModule[it] }.filterNotNull()


        private fun toRubyMethods(methods: List<Method>): List<RubyMethod> =
                methods.map {
                    RubyMethod.Impl(
                            it.name,
                            if (it.path != null) {
                                Location.Impl(it.path!!, it.line!!.toInt())
                            } else null,
                            it.parameters.map {
                                RubyMethod.ArgInfo(RubyMethod.ArgumentKind.fromString(it[0]),
                                        if (it.size == 2) it[1] else "")
                            })
                }


        private fun minimizeIncluded(module: Module, includeGetter: (Module) -> (List<String>)): List<String> {
            val toRemove = HashSet<String>()
            includeGetter(module).forEach {
                if (it != module.name && !toRemove.contains(it) && name2Module.containsKey(it)) {
                    val mod = name2Module[it]!!
                    toRemove.addAll(includeGetter(mod))
                }
            }
            return includeGetter(module).filter { !toRemove.contains(it) }
        }
    }

    private class TopsortHelper(val modules: List<Module>) {
        private val visited = HashSet<String>()
        private val result = ArrayList<Module>()
        private val name2Module = modules.associateBy { it.name }

        fun topsort(): List<Module> {
            visited.add("")
            modules.forEach({ tryVisit(it.name)})
            return result
        }

        private fun dfs(module: Module) {
            tryVisit(module.superclass)
            module.included.forEach {tryVisit(it)}
            module.singleton_class_included.forEach {tryVisit(it)}
            result.add(module)
        }

        private fun tryVisit(name: String) {
            if (!visited.contains(name)) {
                visited.add(name)
                name2Module[name]?.let { dfs(it) }
            }
        }

    }

}
