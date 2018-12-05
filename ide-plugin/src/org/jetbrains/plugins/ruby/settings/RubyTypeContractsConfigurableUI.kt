package org.jetbrains.plugins.ruby.settings

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBPanel
import com.intellij.ui.table.TableView
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.ui.CheckBox
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.GemInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.util.*
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class RubyTypeContractsConfigurableUI(settings: RubyTypeContractsSettings) : ConfigurableUi<RubyTypeContractsSettings> {
    private val toBeRemovedGems = ArrayList<GemInfo>()

    private val registeredGems = ArrayList(RSignatureProviderImpl.registeredGems)

    private val perGemSettingsMap = HashMap(settings.perGemSettingsMap)

    private var typeTrackerEnabled = settings.typeTrackerEnabled
    private var stateTrackerEnabled = settings.stateTrackerEnabled

    private val tableModel = ListTableModel<GemInfo>(
            object : ColumnInfo<GemInfo, String>("Gem Name") {
                override fun valueOf(item: GemInfo?) = item?.name
            },
            object : ColumnInfo<GemInfo, String>("Version") {
                override fun valueOf(item: GemInfo?) = item?.version

                override fun getComparator() = Comparator<GemInfo> { gemInfo1, gemInfo2 ->
                    VersionComparatorUtil.COMPARATOR.compare(gemInfo1.version, gemInfo2.version)
                }
            },
            object : ColumnInfo<GemInfo, Boolean>("Share") {
                private val renderer = BooleanTableCellRenderer()

                private val editor = BooleanTableCellEditor()

                private val width = Math.max(
                        renderer.preferredSize.width,
                        renderer.getFontMetrics(renderer.font).stringWidth(name) + 10)

                override fun getWidth(table: JTable?): Int = width

                override fun valueOf(item: GemInfo?) = perGemSettingsMap[item]?.share != false && item?.name != LOCAL_SOURCE_GEM_NAME

                override fun isCellEditable(item: GemInfo?) = item?.name != LOCAL_SOURCE_GEM_NAME

                override fun getRenderer(item: GemInfo?) = renderer

                override fun getEditor(item: GemInfo?) = editor

                override fun setValue(item: GemInfo, value: Boolean) {
                    if (value) {
                        perGemSettingsMap.remove(item)
                    } else {
                        perGemSettingsMap.put(GemInfoBean(item.name, item.version), PerGemSettings(false))
                    }
                }
            }
    )

    private val tableView = TableView<GemInfo>(tableModel)

    init {
        tableView.intercellSpacing = JBUI.emptySize()
        tableView.isStriped = true
        tableView.cellSelectionEnabled = false
        tableView.rowSelectionAllowed = true
        tableView.showHorizontalLines = false
        tableView.showVerticalLines = false
        tableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        refill()
    }

    override fun reset(settings: RubyTypeContractsSettings) {
        perGemSettingsMap.clear()
        perGemSettingsMap.putAll(settings.perGemSettingsMap)
        typeTrackerEnabled = settings.typeTrackerEnabled
        stateTrackerEnabled = settings.stateTrackerEnabled
        toBeRemovedGems.clear()
        refill()
    }

    override fun isModified(settings: RubyTypeContractsSettings): Boolean {
        return perGemSettingsMap != settings.perGemSettingsMap || toBeRemovedGems.isNotEmpty()
                || settings.stateTrackerEnabled != stateTrackerEnabled
                || settings.typeTrackerEnabled != typeTrackerEnabled
    }

    override fun apply(settings: RubyTypeContractsSettings) {
        if (toBeRemovedGems.isNotEmpty()) {
            transaction {
                toBeRemovedGems.forEach {
                    GemInfoTable.deleteWhere { GemInfoTable.name eq it.name and (GemInfoTable.version eq it.version) }
                }
                perGemSettingsMap.keys.removeAll(toBeRemovedGems)
                registeredGems.removeAll(toBeRemovedGems)
            }
        }
        settings.stateTrackerEnabled = stateTrackerEnabled
        settings.typeTrackerEnabled = typeTrackerEnabled
        settings.perGemSettingsMap = HashMap(perGemSettingsMap)
        refill()
    }

    override fun getComponent(): JComponent {
        val panel = JBPanel<JBPanel<*>>(VerticalFlowLayout())
        panel.add(ToolbarDecorator.createDecorator(tableView.component)
                .setRemoveAction { _ ->
                    val selectedObject = tableView.selectedObject
                            ?: return@setRemoveAction
                    val selectedRowNumber = tableView.selectedRow

                    toBeRemovedGems.add(selectedObject)
                    tableModel.removeRow(tableView.selectedRow)

                    tableView.selectionModel.setSelectionInterval(-239, Math.min(selectedRowNumber, tableModel.rowCount - 1))
                }
                .disableAddAction()
                .disableUpDownActions().createPanel())
        panel.add(CheckBox("Use state tracker results for completion", this, "stateTrackerEnabled"))
        panel.add(CheckBox("Use type tracker results for completion", this, "typeTrackerEnabled"))
        return panel
    }

    private fun refill() {
        tableModel.items = registeredGems.map { GemInfoBean(it.name, it.version) }
    }

    companion object {
        private val LOCAL_SOURCE_GEM_NAME = "LOCAL"
    }
}
