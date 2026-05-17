package com.github.cvzakharchenko.freddie.settings

import com.intellij.ui.CheckBoxWithColorChooser
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class FreddieSettingsConfigurable : SearchableConfigurable {
    private var panel: JPanel? = null
    private var enabledCheckBox: JBCheckBox? = null
    private var triggerOnEditCheckBox: JBCheckBox? = null
    private var chainedSuggestionsCheckBox: JBCheckBox? = null
    private var pauseOnDismissCheckBox: JBCheckBox? = null
    private var suggestionDisplayModeComboBox: JComboBox<FreddieSuggestionDisplayMode>? = null
    private var lineHintInsertedColorChooser: CheckBoxWithColorChooser? = null
    private var lineHintMatchedColorChooser: CheckBoxWithColorChooser? = null
    private var ghostTextInsertedBackgroundChooser: CheckBoxWithColorChooser? = null
    private var ghostTextMatchedBackgroundChooser: CheckBoxWithColorChooser? = null
    private var debounceSpinner: JSpinner? = null
    private var apiKeyField: JPasswordField? = null

    override fun getId(): String = "com.github.cvzakharchenko.freddie.settings"

    override fun getDisplayName(): String = "Freddie"

    override fun createComponent(): JComponent {
        val enabled = JBCheckBox("Enable Mercury next edit prediction")
        val triggerOnEdit = JBCheckBox("Trigger on edit")
        val chainedSuggestions = JBCheckBox("Chained suggestions")
        val pauseOnDismiss = JBCheckBox("Pause on dismiss")
        val suggestionDisplayMode = JComboBox(FreddieSuggestionDisplayMode.entries.toTypedArray())
        val lineHintInsertedColor =
            CheckBoxWithColorChooser(
                "Use custom inserted line hint color",
                false,
                DEFAULT_CUSTOM_LINE_HINT_INSERTED_COLOR,
            )
        val lineHintMatchedColor =
            CheckBoxWithColorChooser(
                "Draw matched line hint segments",
                false,
                DEFAULT_CUSTOM_LINE_HINT_MATCHED_COLOR,
            )
        val ghostTextInsertedBackground =
            CheckBoxWithColorChooser(
                "Use custom inserted ghost text background",
                false,
                DEFAULT_CUSTOM_GHOST_TEXT_INSERTED_BACKGROUND,
            )
        val ghostTextMatchedBackground =
            CheckBoxWithColorChooser(
                "Use custom matched ghost text background",
                false,
                DEFAULT_CUSTOM_GHOST_TEXT_MATCHED_BACKGROUND,
            )
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
        triggerOnEditCheckBox = triggerOnEdit
        chainedSuggestionsCheckBox = chainedSuggestions
        pauseOnDismissCheckBox = pauseOnDismiss
        suggestionDisplayModeComboBox = suggestionDisplayMode
        lineHintInsertedColorChooser = lineHintInsertedColor
        lineHintMatchedColorChooser = lineHintMatchedColor
        ghostTextInsertedBackgroundChooser = ghostTextInsertedBackground
        ghostTextMatchedBackgroundChooser = ghostTextMatchedBackground
        debounceSpinner = debounce
        apiKeyField = apiKey

        panel =
            JPanel(GridBagLayout()).apply {
                border = JBUI.Borders.empty(12)
                addRow(0, JBLabel("Mercury API key"), apiKey)
                addRow(1, JBLabel("Typing debounce, ms"), debounce)
                addRow(2, JBLabel("Suggestion display"), suggestionDisplayMode)
                add(
                    lineHintInsertedColor,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 3
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(4)
                    },
                )
                add(
                    lineHintMatchedColor,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 4
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(4)
                    },
                )
                add(
                    ghostTextInsertedBackground,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 5
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(4)
                    },
                )
                add(
                    ghostTextMatchedBackground,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 6
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(4)
                    },
                )
                add(
                    enabled,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 7
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(8)
                    },
                )
                add(
                    triggerOnEdit,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 8
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(4)
                    },
                )
                add(
                    chainedSuggestions,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 9
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(4)
                    },
                )
                add(
                    pauseOnDismiss,
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 10
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(4)
                    },
                )
                add(
                    JBLabel("Stored in PasswordSafe. If empty, INCEPTION_API_KEY is used."),
                    GridBagConstraints().apply {
                        gridx = 1
                        gridy = 11
                        anchor = GridBagConstraints.WEST
                        insets = JBUI.insetsTop(8)
                    },
                )
                add(
                    JPanel(),
                    GridBagConstraints().apply {
                        gridx = 0
                        gridy = 12
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
            triggerOnEditCheckBox?.isSelected != settings.triggerOnEdit ||
            chainedSuggestionsCheckBox?.isSelected != settings.chainedSuggestionsEnabled ||
            pauseOnDismissCheckBox?.isSelected != settings.pauseOnDismiss ||
            suggestionDisplayModeComboBox?.selectedItem != settings.suggestionDisplayMode ||
            selectedCustomLineHintInsertedColor() != settings.customLineHintInsertedColor ||
            selectedCustomLineHintMatchedColor() != settings.customLineHintMatchedColor ||
            selectedCustomGhostTextInsertedBackground() != settings.customGhostTextInsertedBackgroundColor ||
            selectedCustomGhostTextMatchedBackground() != settings.customGhostTextMatchedBackgroundColor ||
            debounceSpinner?.value != settings.debounceMs ||
            apiKeyText() != (MercuryApiKeyStore.getStoredApiKey() ?: "")
    }

    override fun apply() {
        val settings = FreddieSettings.getInstance()
        settings.nextEditEnabled = enabledCheckBox?.isSelected == true
        settings.triggerOnEdit = triggerOnEditCheckBox?.isSelected == true
        settings.chainedSuggestionsEnabled = chainedSuggestionsCheckBox?.isSelected == true
        settings.pauseOnDismiss = pauseOnDismissCheckBox?.isSelected == true
        settings.suggestionDisplayMode =
            suggestionDisplayModeComboBox?.selectedItem as? FreddieSuggestionDisplayMode
                ?: FreddieSuggestionDisplayMode.GHOST_TEXT
        settings.customLineHintInsertedColor = selectedCustomLineHintInsertedColor()
        settings.customLineHintMatchedColor = selectedCustomLineHintMatchedColor()
        settings.customGhostTextInsertedBackgroundColor = selectedCustomGhostTextInsertedBackground()
        settings.customGhostTextMatchedBackgroundColor = selectedCustomGhostTextMatchedBackground()
        settings.debounceMs = debounceSpinner?.value as? Int ?: 200
        MercuryApiKeyStore.setApiKey(apiKeyText())
    }

    override fun reset() {
        val settings = FreddieSettings.getInstance()
        enabledCheckBox?.isSelected = settings.nextEditEnabled
        triggerOnEditCheckBox?.isSelected = settings.triggerOnEdit
        chainedSuggestionsCheckBox?.isSelected = settings.chainedSuggestionsEnabled
        pauseOnDismissCheckBox?.isSelected = settings.pauseOnDismiss
        suggestionDisplayModeComboBox?.selectedItem = settings.suggestionDisplayMode
        resetLineHintInsertedColor(settings.customLineHintInsertedColor)
        resetLineHintMatchedColor(settings.customLineHintMatchedColor)
        resetGhostTextInsertedBackground(settings.customGhostTextInsertedBackgroundColor)
        resetGhostTextMatchedBackground(settings.customGhostTextMatchedBackgroundColor)
        debounceSpinner?.value = settings.debounceMs
        apiKeyField?.text = MercuryApiKeyStore.getStoredApiKey() ?: ""
    }

    override fun disposeUIResources() {
        panel = null
        enabledCheckBox = null
        triggerOnEditCheckBox = null
        chainedSuggestionsCheckBox = null
        pauseOnDismissCheckBox = null
        suggestionDisplayModeComboBox = null
        lineHintInsertedColorChooser = null
        lineHintMatchedColorChooser = null
        ghostTextInsertedBackgroundChooser = null
        ghostTextMatchedBackgroundChooser = null
        debounceSpinner = null
        apiKeyField = null
    }

    private fun apiKeyText(): String = String(apiKeyField?.password ?: CharArray(0))

    private fun selectedCustomLineHintInsertedColor(): Color? {
        return selectedColor(lineHintInsertedColorChooser)
    }

    private fun selectedCustomLineHintMatchedColor(): Color? {
        return selectedColor(lineHintMatchedColorChooser)
    }

    private fun selectedCustomGhostTextInsertedBackground(): Color? {
        return selectedColor(ghostTextInsertedBackgroundChooser)
    }

    private fun selectedCustomGhostTextMatchedBackground(): Color? {
        return selectedColor(ghostTextMatchedBackgroundChooser)
    }

    private fun resetLineHintInsertedColor(color: Color?) {
        resetColor(lineHintInsertedColorChooser, color, DEFAULT_CUSTOM_LINE_HINT_INSERTED_COLOR)
    }

    private fun resetLineHintMatchedColor(color: Color?) {
        resetColor(lineHintMatchedColorChooser, color, DEFAULT_CUSTOM_LINE_HINT_MATCHED_COLOR)
    }

    private fun resetGhostTextInsertedBackground(color: Color?) {
        resetColor(ghostTextInsertedBackgroundChooser, color, DEFAULT_CUSTOM_GHOST_TEXT_INSERTED_BACKGROUND)
    }

    private fun resetGhostTextMatchedBackground(color: Color?) {
        resetColor(ghostTextMatchedBackgroundChooser, color, DEFAULT_CUSTOM_GHOST_TEXT_MATCHED_BACKGROUND)
    }

    private fun selectedColor(chooser: CheckBoxWithColorChooser?): Color? {
        return chooser?.takeIf { it.isSelected }?.color
    }

    private fun resetColor(
        chooser: CheckBoxWithColorChooser?,
        color: Color?,
        defaultColor: Color,
    ) {
        chooser ?: return
        chooser.setSelected(color != null)
        chooser.setColor(color ?: defaultColor)
    }

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

    companion object {
        private val DEFAULT_CUSTOM_LINE_HINT_INSERTED_COLOR = Color(0x48C774)
        private val DEFAULT_CUSTOM_LINE_HINT_MATCHED_COLOR = Color(0x7A7F86)
        private val DEFAULT_CUSTOM_GHOST_TEXT_INSERTED_BACKGROUND = Color(0x1F6F43)
        private val DEFAULT_CUSTOM_GHOST_TEXT_MATCHED_BACKGROUND = Color(0x3F4247)
    }
}
