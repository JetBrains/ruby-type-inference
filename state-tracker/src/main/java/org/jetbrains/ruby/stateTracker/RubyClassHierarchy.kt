package org.jetbrains.ruby.stateTracker

interface RubyClassHierarchy {
    val loadPaths: List<String>

    val topLevelConstants: Map<String, RubyConstant>

    fun getRubyModule(fqn: String) : RubyModule?

    class Impl(override val loadPaths: List<String>, rubyModules: List<RubyModule>,
               override val topLevelConstants: Map<String, RubyConstant>) : RubyClassHierarchy {

        private val name2modules  = rubyModules.associateBy( {it.name} , {it})

        override fun getRubyModule(fqn: String): RubyModule? {
            return name2modules[fqn]
        }
    }
}

interface RubyConstant {
    val name: String
    val type: String
    val extended: List<String>
    data class Impl(override val name: String,
                    override val type: String,
                    override val extended: List<String>) : RubyConstant
}

interface RubyModule {
    val name: String
    val classDirectAncestors: List<RubyModule>
    val instanceDirectAncestors: List<RubyModule>
    val classMethods: List<RubyMethod>
    val instanceMethods: List<RubyMethod>

    class Impl(override val name: String,
               override val classDirectAncestors: List<RubyModule>,
               override val instanceDirectAncestors: List<RubyModule>,
               override val classMethods: List<RubyMethod>,
               override val instanceMethods: List<RubyMethod>) : RubyModule
}

interface RubyClass: RubyModule {
    val superClass : RubyClass

    class Impl(override val name: String,
               override val classDirectAncestors: List<RubyModule>,
               override val instanceDirectAncestors: List<RubyModule>,
               override val classMethods: List<RubyMethod>,
               override val instanceMethods: List<RubyMethod>,
               override val superClass: RubyClass) : RubyClass

    companion object : RubyClass {
        val EMPTY = this
        override val name: String
            get() = ""
        override val classDirectAncestors: List<RubyModule>
            get() = emptyList()
        override val instanceDirectAncestors: List<RubyModule>
            get() = emptyList()
        override val classMethods: List<RubyMethod>
            get() = emptyList()
        override val instanceMethods: List<RubyMethod>
            get() = emptyList()
        override val superClass: RubyClass
            get() = this
    }
}

interface RubyMethod {
    val name: String
    val location: Location?
    val arguments: List<ArgInfo>
    data class ArgInfo(val kind: ArgumentKind, val name: String)
    class Impl(override val name: String, override val location: Location?,
               override val arguments: List<ArgInfo>) : RubyMethod
    enum class ArgumentKind {
        REQ,
        OPT,
        REST,
        KEY,
        KEY_REST,
        KEY_REQ,
        BLOCK;

        companion object {
            fun fromString(name : String): ArgumentKind {
                return when (name) {
                    "req"      -> REQ
                    "opt"      -> OPT
                    "rest"     -> REST
                    "key"      -> KEY
                    "keyrest"  -> KEY_REST
                    "keyreq"   -> KEY_REQ
                    "block"    -> BLOCK
                    else -> throw IllegalArgumentException(name)
                }
            }
        }
    }

}

interface Location {
    val path: String
    val lineNo: Int

    data class Impl(override val path: String, override val lineNo: Int) : Location
}


