package org.jetbrains.plugins.ruby.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo

@State(
        name = "RubyTypeContractsSettings",
        storages = arrayOf(Storage("ruby_type_inference.xml"))
)
data class RubyTypeContractsSettings @JvmOverloads constructor(
        @Attribute
        var localSourcesTrackingPolicy: LocalSourcesTrackingPolicy = LocalSourcesTrackingPolicy.ACCUMULATE,
        @MapAnnotation
        var perGemSettingsMap: MutableMap<GemInfoBean, PerGemSettings> = HashMap(),
        @Attribute("typeTrackerEnabled")
        var typeTrackerEnabled: Boolean = false,
        @Attribute("stateTrackerEnabled")
        var stateTrackerEnabled: Boolean = true,
        @Attribute("returnTypeTrackerEnabled")
        var returnTypeTrackerEnabled: Boolean = true)

    : PersistentStateComponent<RubyTypeContractsSettings> {
    override fun loadState(state: RubyTypeContractsSettings?) {
        if (state == null) {
            return
        }

        XmlSerializerUtil.copyBean(state, this)
    }

    override fun getState(): RubyTypeContractsSettings? = this
}

enum class LocalSourcesTrackingPolicy {
    IGNORE,
    CLEAR_ON_CHANGES,
    ACCUMULATE
}

data class GemInfoBean(@Attribute("name") override val name: String = "",
                       @Attribute("version") override val version: String = "") : GemInfo

data class PerGemSettings(@Attribute("share") val share: Boolean) {
    @Suppress("unused")
    private constructor() : this(true)
}