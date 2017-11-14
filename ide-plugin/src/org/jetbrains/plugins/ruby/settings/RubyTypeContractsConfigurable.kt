package org.jetbrains.plugins.ruby.settings

import com.intellij.openapi.options.ConfigurableBase

class RubyTypeContractsConfigurable(private val settings: RubyTypeContractsSettings) :
        ConfigurableBase<RubyTypeContractsConfigurableUI,
                         RubyTypeContractsSettings>(RubyTypeContractsConfigurable::class.java.name,
                                                    "Ruby Type Contracts", null) {

    override fun getSettings(): RubyTypeContractsSettings {
        return settings
    }

    override fun createUi(): RubyTypeContractsConfigurableUI {
        return RubyTypeContractsConfigurableUI(settings)
    }

}
