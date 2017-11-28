package org.jetbrains.ruby.stateTracker

import com.google.gson.Gson
import java.util.*
import kotlin.collections.ArrayList

object RubyClassHierarchyLoader {

    private val gson = Gson()


    fun mergeJsons(jsons: List<String>): String {
        return gson.toJson(jsons.map { gson.fromJson(it, Root::class.java) }.reduce { a, b -> joinRoots(a, b) })
    }

    fun fromJson(json: String): RubyClassHierarchy {
        val root = gson.fromJson(json, Root::class.java)
        return RubyClassHierarchy.Impl(root.load_path,
                                       MapHelper(TopsortHelper(root.modules).topsort()).map(),
                                       root.top_level_constants.associate { Pair(it.name, RubyConstant.Impl(
                                               it.name,
                                               it.class_name,
                                               it.extended
                                       )) })
    }

    private fun joinRoots(one: Root, another: Root) : Root {
        return Root( joinTopLevelConstants(one.top_level_constants, another.top_level_constants),
                     joinLoadPath(one.load_path, another.load_path),
                     joinModules(one.modules, another.modules)
        )
    }

    private fun joinTopLevelConstants(one: List<TopLevelConstant>, another: List<TopLevelConstant>): List<TopLevelConstant> {
        return one.union(another).associateBy(TopLevelConstant::name).values.toList()
    }

    private fun joinLoadPath(one: List<String>, another: List<String>): List<String> = one.union(another).toList()

    private fun joinModules(one: List<Module>, another: List<Module>): List<Module> {
        return one.union(another).associateBy(Module::name).values.toList()
    }

    private data class Method(var name: String,
                      var path: String?,
                      var line: String?,
                      var parameters: List<List<String>>)

    private data class Module(var name: String,
                              var type: String,
                              var superclass: String?,
                              var singleton_class_included: List<String>,
                              var included: List<String>,
                              var class_methods: List<Method>,
                              var instance_methods: List<Method>)

    private data class TopLevelConstant(var name: String,
                                        var class_name: String,
                                        var extended: List<String>)

    private data class Root(var top_level_constants: List<TopLevelConstant>,
                            var load_path: List<String>, var modules: List<Module>)

    private class MapHelper(val topsortedList: List<Module>) {
        private val name2RubyModule = HashMap<String, RubyModule>()
        private val name2Module = topsortedList.associateBy { it.name }

        fun map() : List<RubyModule> = topsortedList.map { toRubyModule(it) }

        private fun toRubyModule(module: Module): RubyModule {
            val rubyModule = when (module.type) {
                "Module" -> createModule(module)
                "Class" -> createClass(module)
                //TODO some module/class objects can have type derived from Module/Class.
                //For example: RSpec::Rails::AssertionDelegator
                //lets have a look at superclass
                else -> if (module.superclass.isNullOrBlank()) createModule(module) else createClass(module)
            }
            name2RubyModule[rubyModule.name] = rubyModule
            return rubyModule
        }

        private fun createClass(module: Module): RubyClass.Impl {
            return RubyClass.Impl(module.name,
                    toRubyModules(minimizeIncluded(module, { m -> m.singleton_class_included })),
                    toRubyModules(minimizeIncluded(module, { m -> m.included })),
                    toRubyMethods(module.class_methods),
                    toRubyMethods(module.instance_methods),
                    (name2RubyModule[module.superclass] ?: RubyClass.EMPTY) as RubyClass)
        }

        private fun createModule(module: Module): RubyModule.Impl {
            return RubyModule.Impl(module.name,
                    toRubyModules(minimizeIncluded(module, { m -> m.singleton_class_included })),
                    toRubyModules(minimizeIncluded(module, { m -> m.included })),
                    toRubyMethods(module.class_methods),
                    toRubyMethods(module.instance_methods))
        }

        private fun toRubyModules(names: List<String>): List<RubyModule> =
                names.mapNotNull { name2RubyModule[it] }


        private fun toRubyMethods(methods: List<Method>): List<RubyMethod> =
                methods.map {
                    RubyMethod.Impl(
                            it.name,
                            if (it.path != null && it.line != null) {
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
                if (it != module.name && !toRemove.contains(it)) {
                    name2Module[it]?.let {
                        toRemove.addAll(includeGetter(it))
                    }
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

        private fun tryVisit(name: String?) {
            if (name == null) return
            if (visited.add(name)) {
                name2Module[name]?.let { dfs(it) }
            }
        }

    }

}

