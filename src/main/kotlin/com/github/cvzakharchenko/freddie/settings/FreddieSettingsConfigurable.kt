package com.github.cvzakharchenko.freddie.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class FreddieSettingsConfigurable : SearchableConfigurable {
    private var panel: JPanel? = null
    private var enabledCheckBox: JBCheckBox? = null
    private var debounceSpinner: JSpinner? = null
    private var apiKeyField: JPasswordField? = null

    override fun getId(): String = "com.github.cvzakharchenko.freddie.settings"

    override fun getDisplayName(): String = "Freddie"

    override fun createComponent(): JComponent {
        val enabled = JBCheckBox("Enable Mercury next edit prediction")
        val debounce =
            JSpinner(
                SpinnerNumberModel(
                    200,
                    FreddieSettings.MIN_DEBOUNCE_MS,
                    FreddieSettings.MAX_DEBOUNCE_MS,
                    10,
                ),
            )
        val apiKey = JPasswordField(36)

        enabledCheckBox = enabled
        debounceSpinner = debounce
        apiKeyField = apiKey

        panel =
            JPanel(GridBagLayout()).apply {
                border = JBUI.Borders.empty(12)
                addRow(0, JBLabel("Mercury API key"), apiKey)
                addRow(1, JBLabel("Typing debounce, ms"), debounce)
                add(
                    enabled,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 2
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(8)
                    },
                )
                add(
                    JBLabel("Stored in PasswordSafe. If empty, INCEPTION_API_KEY is used."),
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 3
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(8)
                    },
                )
                add(
                    JPanel(),
                    GridBagConstraints().apply {
                        gridx = 0
                        gridy = 4
                        weightx = 1.0
                        weighty = 1.0
                        fill = GridBagConstraints.BOTH
                    },
                )
            }

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = FreddieSettings.getInstance()
        return enabledCheckBox?.isSelected != settings.nextEditEnabled ||
            debounceSpinner?.value != settings.debounceMs ||
            apiKeyText() != (MercuryApiKeyStore.getStoredApiKey() ?: "")
    }

    override fun apply() {
        val settings = FreddieSettings.getInstance()
        settings.nextEditEnabled = enabledCheckBox?.isSelected == true
        settings.debounceMs = debounceSpinner?.value as? Int ?: 200
        MercuryApiKeyStore.setApiKey(apiKeyText())
    }

    override fun reset() {
        val settings = FreddieSettings.getInstance()
        enabledCheckBox?.isSelected = settings.nextEditEnabled
        debounceSpinner?.value = settings.debounceMs
        apiKeyField?.text = MercuryApiKeyStore.getStoredApiKey() ?: ""
    }

    override fun disposeUIResources() {
        panel = null
        enabledCheckBox = null
        debounceSpinner = null
        apiKeyField = null
    }

    private fun apiKeyText(): String = String(apiKeyField?.password ?: CharArray(0))

    private fun JPanel.addRow(
        row: Int,
        label: JComponent,
        field: JComponent,
    ) {
        add(
            label,
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, 8, 12)
            },
        )
        add(
            field,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsBottom(8)
            },
        )
    }
}
