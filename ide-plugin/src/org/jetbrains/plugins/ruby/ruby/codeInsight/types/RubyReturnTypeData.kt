package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.google.gson.Gson

interface RubyReturnTypeData {
    fun getTypeByFQNAndMethodName(fqn: String, name: String) : List<String>?

    companion object {
        private val gson = Gson()

        fun createFromJson(json: String) : RubyReturnTypeData {
            return Impl(gson.fromJson(json, Array<Schema>::class.java))
        }
    }

    private data class Schema(var def : String, var name : String, var ret: String)

    private class Impl(calls: Array<Schema>) : RubyReturnTypeData {
        private val method2Types = HashMap<Pair<String, String>, MutableList<String>>()
        init {
            calls.forEach {
                val pair = Pair(it.def, it.name)
                if (!method2Types.containsKey(pair)) {
                    method2Types[pair] = ArrayList()
                }
                method2Types[pair]!!.add(it.ret)
            }
        }

        override fun getTypeByFQNAndMethodName(fqn: String, name: String): List<String>? {
            return method2Types[Pair(fqn, name)]
        }
    }
}


