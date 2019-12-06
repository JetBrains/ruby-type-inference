package org.jetbrains.ruby.codeInsight

/**
 * Dependency injection mechanism
 */
interface Injector {
    fun <T> getLogger(cl: Class<T>): Logger
}

@Volatile
private var _injector: Injector? = null
val injector: Injector
    get() {
        return _injector ?: throw IllegalStateException("Injector must be initialized before any usage")
    }

// Because the we don't know anything about injector initializators we assume that it can be
// potentially multi threaded but necessity of injector initialization thread safety isn't really investigated
@Synchronized
fun initInjector(injector: Injector) {
    check(_injector == null) {
        "Injector must be initialized only once"
    }
    _injector = injector
}
