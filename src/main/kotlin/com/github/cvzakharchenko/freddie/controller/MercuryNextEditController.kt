package com.github.cvzakharchenko.freddie.controller

import com.github.cvzakharchenko.freddie.context.EditableRegionMatcher
import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer
import com.github.cvzakharchenko.freddie.context.MercuryContextCollector
import com.github.cvzakharchenko.freddie.context.MercuryRequestSnapshot
import com.github.cvzakharchenko.freddie.context.RecentEditHistory
import com.github.cvzakharchenko.freddie.context.RecentlyViewedSnippetTracker
import com.github.cvzakharchenko.freddie.context.CopiedSnippetTracker
import com.github.cvzakharchenko.freddie.context.projectRelativePath
import com.github.cvzakharchenko.freddie.debug.FreddieDebugStateService
import com.github.cvzakharchenko.freddie.mercury.MercuryApiException
import com.github.cvzakharchenko.freddie.mercury.MercuryClient
import com.github.cvzakharchenko.freddie.mercury.MercuryCompletion
import com.github.cvzakharchenko.freddie.presentation.ChangedBlock
import com.github.cvzakharchenko.freddie.presentation.MercurySuggestion
import com.github.cvzakharchenko.freddie.presentation.PresentedSuggestion
import com.github.cvzakharchenko.freddie.presentation.SettingsBackedSuggestionPresenter
import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.github.cvzakharchenko.freddie.settings.FreddieSuggestionDisplayMode
import com.github.cvzakharchenko.freddie.settings.MercuryApiKeyStore
import com.github.cvzakharchenko.freddie.trigger.DismissPauseState
import com.github.cvzakharchenko.freddie.trigger.MercuryTriggerPolicy
import com.github.cvzakharchenko.freddie.trigger.TriggerKind
import com.intellij.ide.IdeEventQueue
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import java.awt.AWTEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
class MercuryNextEditController(
    private val project: Project,
) : Disposable {
    private data class ResolvedSuggestionRegion(
        val startOffset: Int,
        val endOffset: Int,
        val note: String?,
    )

    private data class RegionCheck(
        val region: ResolvedSuggestionRegion? = null,
        val discardReason: String? = null,
    )

    private data class DocumentTracking(
        val document: Document,
        val disposable: Disposable,
        var lastText: String,
        var lastEditor: Editor? = null,
    )

    private enum class PreviewSource(
        val label: String,
        val debug: Boolean,
    ) {
        MERCURY(label = "Suggestion", debug = false),
        DEBUG_EDIT(label = "Debug preview", debug = true),
    }

    private val recentEditHistory = RecentEditHistory()
    private val recentlyViewedSnippetTracker = RecentlyViewedSnippetTracker(project)
    private val copiedSnippetTracker = project.service<CopiedSnippetTracker>()
    private val contextCollector = MercuryContextCollector(project, recentEditHistory, recentlyViewedSnippetTracker, copiedSnippetTracker)
    private val triggerPolicy = MercuryTriggerPolicy(project)
    private val dismissPauseState = DismissPauseState()
    private val debugState = project.service<FreddieDebugStateService>()
    private val presenter = SettingsBackedSuggestionPresenter()
    private val mercuryClient = MercuryClient()
    private val typedRequestAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val requestSerial = AtomicLong()
    private val editorDisposables = linkedMapOf<Editor, Disposable>()
    private val documentTrackings = linkedMapOf<Document, DocumentTracking>()

    private var started = false
    private var currentTask: Future<*>? = null
    private var currentPresentedSuggestion: PresentedSuggestion? = null
    private var lastPreviewSnapshot: MercuryRequestSnapshot? = null
    private var suppressDocumentTriggers = false
    private var altGhostTextOverride = false

    fun start() {
        if (started || project.isDisposed) return
        started = true
        debugState.recordStartup()

        EditorFactory.getInstance().allEditors.forEach { attachEditor(it) }
        EditorFactory.getInstance().addEditorFactoryListener(
            object : EditorFactoryListener {
                override fun editorCreated(event: EditorFactoryEvent) {
                    attachEditor(event.editor)
                }

                override fun editorReleased(event: EditorFactoryEvent) {
                    clearVisibleSuggestionIfOwnedBy(event.editor)
                    editorDisposables.remove(event.editor)?.let { Disposer.dispose(it) }
                    documentTrackings[event.editor.document]?.let { tracking ->
                        if (tracking.lastEditor === event.editor) {
                            tracking.lastEditor = null
                        }
                    }
                }
            },
            this,
        )

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    invalidatePendingRequests()
                    clearVisibleSuggestion()
                    debugState.recordEvent("File selection changed; pending request and preview cleared")
                    FileEditorManager.getInstance(project).selectedTextEditor?.let { recordEditorVisit(it) }
                }
            },
        )

        IdeEventQueue.getInstance().addDispatcher(
            IdeEventQueue.EventDispatcher { event ->
                handleModifierKeyEvent(event)
                false
            },
            this,
        )
    }

    fun requestSuggestion(
        editor: Editor,
        triggerKind: TriggerKind,
    ) {
        if (project.isDisposed || editor.isDisposed || editor.project != project) {
            debugState.recordRequestSkipped(triggerKind, "project/editor is no longer valid")
            return
        }

        typedRequestAlarm.cancelAllRequests()
        if (triggerKind == TriggerKind.MANUAL && dismissPauseState.resume()) {
            debugState.recordEditTriggerPause(false, "Manual request resumed edit-triggered suggestions")
        }

        if (!FreddieSettings.getInstance().nextEditEnabled) {
            debugState.recordRequestSkipped(triggerKind, "next edit prediction is disabled")
            if (triggerKind == TriggerKind.MANUAL) {
                notifyUser("Mercury next edit is disabled", "Enable Freddie in Tools > Freddie to request suggestions.")
            }
            return
        }

        val apiKey = MercuryApiKeyStore.getApiKeyOrEnv()
        if (apiKey.isNullOrBlank()) {
            debugState.recordRequestSkipped(triggerKind, "Mercury API key is missing")
            if (triggerKind == TriggerKind.MANUAL) {
                notifyUser("Missing Mercury API key", "Set a Mercury API key in Tools > Freddie or INCEPTION_API_KEY.")
            }
            return
        }

        recordEditorVisit(editor)
        val snapshot = contextCollector.capture(editor)
        if (snapshot == null) {
            debugState.recordRequestSkipped(triggerKind, "editor context could not be collected")
            if (triggerKind == TriggerKind.MANUAL) {
                notifyUser("No editor context", "Freddie could not collect context for this editor.")
            }
            return
        }

        val requestId = requestSerial.incrementAndGet()
        lastPreviewSnapshot = snapshot
        debugState.recordRequestStarted(triggerKind, requestId, snapshot, visibleSuggestion = currentPresentedSuggestion != null)
        currentTask?.cancel(true)
        currentTask =
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val completion = mercuryClient.requestNextEdit(snapshot.prompt, apiKey)
                    ApplicationManager.getApplication().invokeLater(
                        { handleCompletion(requestId, snapshot, completion) },
                        ModalityState.any(),
                    )
                } catch (error: MercuryApiException) {
                    ApplicationManager.getApplication().invokeLater(
                        { handleRequestError(requestId, triggerKind, error) },
                        ModalityState.any(),
                    )
                } catch (error: Throwable) {
                    ApplicationManager.getApplication().invokeLater(
                        { handleRequestError(requestId, triggerKind, error) },
                        ModalityState.any(),
                    )
                }
            }
    }

    fun acceptCurrentSuggestion(): Boolean {
        return acceptSuggestionReplacement(
            newTextProvider = { suggestion ->
                PartialSuggestionAcceptance.accept(
                    currentText = suggestion.originalText,
                    replacementText = suggestion.replacementText,
                    kind = PartialAcceptKind.BLOCK,
                )
            },
            commandName = "Accept Mercury Next Edit",
            acceptedMessage = "Suggestion block accepted",
            completedMessage = "Suggestion accepted",
        )
    }

    fun acceptNextSuggestionWord(): Boolean =
        acceptSuggestionReplacement(
            newTextProvider = { suggestion ->
                PartialSuggestionAcceptance.accept(
                    currentText = suggestion.originalText,
                    replacementText = suggestion.replacementText,
                    kind = PartialAcceptKind.WORD,
                )
            },
            commandName = "Accept Mercury Next Edit Word",
            acceptedMessage = "Suggestion word accepted",
            completedMessage = "Suggestion completed by accepting a word",
        )

    fun acceptNextSuggestionLine(): Boolean =
        acceptSuggestionReplacement(
            newTextProvider = { suggestion ->
                PartialSuggestionAcceptance.accept(
                    currentText = suggestion.originalText,
                    replacementText = suggestion.replacementText,
                    kind = PartialAcceptKind.LINE,
                )
            },
            commandName = "Accept Mercury Next Edit Line",
            acceptedMessage = "Suggestion line accepted",
            completedMessage = "Suggestion completed by accepting a line",
        )

    private fun acceptSuggestionReplacement(
        newTextProvider: (MercurySuggestion) -> PartialAcceptResult?,
        commandName: String,
        acceptedMessage: String,
        completedMessage: String,
    ): Boolean {
        val presented = currentPresentedSuggestion ?: return false
        val suggestion = presented.suggestion
        val regionCheck = resolveSuggestionRegion(suggestion)
        if (regionCheck.discardReason != null) {
            invalidatePendingRequests()
            clearVisibleSuggestion()
            debugState.recordSuggestionResult("Suggestion was not accepted: ${regionCheck.discardReason}", visibleSuggestion = false)
            return false
        }
        val resolvedRegion = requireNotNull(regionCheck.region)
        resolvedRegion.note?.let { debugState.recordEvent("Accepting relocated suggestion: $it") }
        val accepted = newTextProvider(suggestion)
        if (accepted == null || accepted.text == suggestion.originalText) {
            debugState.recordSuggestionResult("Suggestion was not accepted: no partial change is available", visibleSuggestion = true)
            return false
        }

        val beforeText = suggestion.document.text
        suppressDocumentTriggers = true
        try {
            WriteCommandAction.runWriteCommandAction(
                project,
                commandName,
                null,
                Runnable {
                    suggestion.document.replaceString(
                        resolvedRegion.startOffset,
                        resolvedRegion.endOffset,
                        accepted.text,
                    )
                    val newCaretOffset =
                        (
                            resolvedRegion.startOffset +
                                SuggestionCaretMapper.caretAfterLastAppliedBlock(
                                    originalText = suggestion.originalText,
                                    replacementText = accepted.text,
                                )
                        ).coerceIn(0, suggestion.document.textLength)
                    suggestion.editor.caretModel.moveToOffset(newCaretOffset)
                },
            )
        } finally {
            suppressDocumentTriggers = false
        }

        trackingFor(suggestion.document).lastText = suggestion.document.text
        recordEditHistory(suggestion.filePath, beforeText, suggestion.document.text)
        invalidatePendingRequests()
        if (accepted.completed) {
            clearVisibleSuggestion()
            debugState.recordSuggestionResult(completedMessage, visibleSuggestion = false)
            if (FreddieSettings.getInstance().chainedSuggestionsEnabled) {
                requestSuggestion(suggestion.editor, TriggerKind.ACCEPTED_SUGGESTION)
            } else {
                debugState.recordEvent("Chained suggestions disabled; no follow-up request was made")
            }
        } else {
            val updatedSuggestion =
                suggestion.copy(
                    modificationStamp = suggestion.document.modificationStamp,
                    startOffset = resolvedRegion.startOffset,
                    endOffset = resolvedRegion.startOffset + accepted.text.length,
                    originalText = accepted.text,
                    caretOffset = suggestion.editor.caretModel.offset,
                )
            currentPresentedSuggestion = presenter.show(updatedSuggestion)
            debugState.recordSuggestionResult(acceptedMessage, visibleSuggestion = currentPresentedSuggestion != null)
        }
        return beforeText != suggestion.document.text
    }

    fun dismissCurrentSuggestion(): Boolean {
        val hadSuggestion = currentPresentedSuggestion != null
        invalidatePendingRequests()
        clearVisibleSuggestion()
        if (hadSuggestion && dismissPauseState.pauseIfEnabled(FreddieSettings.getInstance().pauseOnDismiss)) {
            debugState.recordEditTriggerPause(true, "Suggestion dismissed; edit-triggered suggestions paused until a manual request")
        } else {
            debugState.recordSuggestionResult("Suggestion dismissed", visibleSuggestion = false)
        }
        return hadSuggestion
    }

    fun hasVisibleSuggestion(): Boolean = currentPresentedSuggestion != null

    fun previewEditedChoiceText(choiceText: String): Boolean {
        val snapshot = lastPreviewSnapshot
        if (snapshot == null) {
            clearVisibleSuggestion()
            debugState.recordDebugPreviewResult("Debug preview skipped: no request snapshot is available", visibleSuggestion = false)
            return false
        }
        if (choiceText.isEmpty()) {
            clearVisibleSuggestion()
            debugState.recordDebugPreviewResult("Debug preview cleared: choice text is empty", visibleSuggestion = false)
            return false
        }
        val replacementText = MercuryClient.cleanNextEditOutput(choiceText)
        if (replacementText == null) {
            clearVisibleSuggestion()
            debugState.recordDebugPreviewResult("Debug preview cleared: choice text is None", visibleSuggestion = false)
            return false
        }
        return showReplacementFromSnapshot(
            snapshot = snapshot,
            rawReplacementText = replacementText,
            source = PreviewSource.DEBUG_EDIT,
        )
    }

    private fun attachEditor(editor: Editor) {
        if (project.isDisposed || editor.isDisposed || editor.project != project) return
        if (editorDisposables.containsKey(editor)) return

        val disposable = Disposer.newDisposable("Freddie editor listeners")
        Disposer.register(this, disposable)
        editorDisposables[editor] = disposable

        val tracking = trackingFor(editor.document)
        tracking.lastEditor = editor
        recordEditorVisit(editor)

        editor.caretModel.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    tracking.lastEditor = editor
                    recordEditorVisit(editor)
                    debugState.recordEvent("Caret moved; visible suggestion kept")
                }
            },
            disposable,
        )
    }

    private fun trackingFor(document: Document): DocumentTracking =
        documentTrackings.getOrPut(document) {
            val disposable = Disposer.newDisposable("Freddie document listener")
            Disposer.register(this, disposable)
            lateinit var tracking: DocumentTracking
            tracking = DocumentTracking(document, disposable, document.text)
            document.addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        onDocumentChanged(tracking, event)
                    }
                },
                disposable,
            )
            tracking
        }

    private fun onDocumentChanged(
        tracking: DocumentTracking,
        event: DocumentEvent,
    ) {
        val oldText = tracking.lastText
        val newText = event.document.text
        tracking.lastText = newText

        if (suppressDocumentTriggers) return

        recordUserEdit(event.document, oldText, newText)

        if (tryAdvanceVisibleSuggestion(oldText, newText, event)) {
            invalidatePendingRequests()
            return
        }

        invalidatePendingRequests()
        clearVisibleSuggestion()

        val editor = tracking.lastEditor?.takeUnless { it.isDisposed } ?: editorFor(event.document) ?: return
        tracking.lastEditor = editor
        val settings = FreddieSettings.getInstance()
        val editTriggersPaused = dismissPauseState.isPaused(settings.pauseOnDismiss)
        val decision = triggerPolicy.decisionAfterTypedEdit(editor, event, editTriggersPaused)
        debugState.recordTypedDecision(decision, settings.debounceMs, editTriggersPaused)
        if (decision.shouldRequest) {
            scheduleTypedRequest(editor)
        }
    }

    private fun scheduleTypedRequest(editor: Editor) {
        typedRequestAlarm.cancelAllRequests()
        typedRequestAlarm.addRequest(
            { requestSuggestion(editor, TriggerKind.TYPED_EDIT) },
            FreddieSettings.getInstance().debounceMs,
        )
    }

    private fun handleCompletion(
        requestId: Long,
        snapshot: MercuryRequestSnapshot,
        completion: MercuryCompletion,
    ) {
        if (requestId != requestSerial.get() || project.isDisposed) {
            debugState.recordEvent("Ignored stale Mercury response for request #$requestId")
            return
        }
        currentTask = null
        debugState.recordResponse(completion)

        val rawReplacementText = completion.replacementText
        if (rawReplacementText == null) {
            debugState.recordSuggestionResult("Mercury returned None or no choice content", visibleSuggestion = currentPresentedSuggestion != null)
            return
        }
        showReplacementFromSnapshot(
            snapshot = snapshot,
            rawReplacementText = rawReplacementText,
            source = PreviewSource.MERCURY,
        )
    }

    private fun showReplacementFromSnapshot(
        snapshot: MercuryRequestSnapshot,
        rawReplacementText: String,
        source: PreviewSource,
    ): Boolean {
        clearVisibleSuggestion()
        val preparedReplacement =
            LineEndingNormalizer.prepareReplacementForEditableRegion(
                mercuryReplacement = rawReplacementText,
                originalEditableRegion = snapshot.editableRegion.originalText,
                documentText = snapshot.document.text,
            )
        if (preparedReplacement.changedLineEndings) {
            debugState.recordEvent("Normalized Mercury replacement line endings to ${describeLineSeparator(preparedReplacement.targetLineSeparator)}")
        }
        if (preparedReplacement.changedLeadingLineEnding) {
            debugState.recordEvent("Removed leading blank line from Mercury replacement to align with the editable region")
        }
        if (preparedReplacement.changedTrailingLineEnding) {
            debugState.recordEvent("Adjusted Mercury replacement trailing line ending to match the editable region")
        }
        val filteredReplacement =
            ChangedBlock.dropLastLineTouchingBlocks(
                original = snapshot.editableRegion.originalText,
                replacement = preparedReplacement.applicationText,
            )
        if (filteredReplacement.droppedBlockCount > 0) {
            debugState.recordEvent("Dropped ${filteredReplacement.droppedBlockCount} suggestion block(s) touching the editable region boundary")
        }
        if (filteredReplacement.droppedBlockCount > 0 && filteredReplacement.keptBlockCount == 0) {
            recordPreviewResult("${source.label} discarded: only boundary-touching blocks remained", visibleSuggestion = false, source = source)
            return false
        }
        val replacementText =
            LineEndingNormalizer.convertLfToLineSeparator(
                filteredReplacement.text,
                preparedReplacement.targetLineSeparator,
            )
        if (replacementText == snapshot.editableRegion.originalText) {
            recordPreviewResult("${source.label} matched the editable region exactly", visibleSuggestion = false, source = source)
            return false
        }

        val regionCheck = resolveSnapshotRegion(snapshot)
        if (regionCheck.discardReason != null) {
            recordPreviewResult("${source.label} discarded: ${regionCheck.discardReason}", visibleSuggestion = false, source = source)
            return false
        }
        val resolvedRegion = requireNotNull(regionCheck.region)
        resolvedRegion.note?.let { debugState.recordEvent("Using relocated editable region: $it") }

        val suggestion =
            MercurySuggestion(
                editor = snapshot.editor,
                document = snapshot.document,
                filePath = snapshot.filePath,
                modificationStamp = snapshot.modificationStamp,
                caretOffset = snapshot.caretOffset,
                startOffset = resolvedRegion.startOffset,
                endOffset = resolvedRegion.endOffset,
                originalText = snapshot.editableRegion.originalText,
                replacementText = replacementText,
            )
        currentPresentedSuggestion = presenter.show(suggestion)
        val visible = currentPresentedSuggestion != null
        val presentation = currentPresentedSuggestion?.presentationDescription
        recordPreviewResult(
            message =
                if (visible) {
                    "${source.label} preview is visible: $presentation"
                } else {
                    "${source.label} was not rendered because the changed block was empty " +
                        "(display mode: ${FreddieSettings.getInstance().suggestionDisplayMode})"
                },
            visibleSuggestion = visible,
            source = source,
        )
        return visible
    }

    private fun recordPreviewResult(
        message: String,
        visibleSuggestion: Boolean,
        source: PreviewSource,
    ) {
        if (source.debug) {
            debugState.recordDebugPreviewResult(message, visibleSuggestion)
        } else {
            debugState.recordSuggestionResult(message, visibleSuggestion)
        }
    }

    private fun tryAdvanceVisibleSuggestion(
        oldDocumentText: String,
        newDocumentText: String,
        event: DocumentEvent,
    ): Boolean {
        val presented = currentPresentedSuggestion ?: return false
        val suggestion = presented.suggestion
        if (suggestion.document != event.document || suggestion.editor.isDisposed) return false

        val update =
            StickySuggestionUpdater.advance(
                oldDocumentText = oldDocumentText,
                newDocumentText = newDocumentText,
                regionStartOffset = suggestion.startOffset,
                regionEndOffset = suggestion.endOffset,
                currentRegionText = suggestion.originalText,
                replacementText = suggestion.replacementText,
                editOffset = event.offset,
                oldLength = event.oldLength,
                newLength = event.newLength,
            ) ?: return false

        if (update.completed) {
            clearVisibleSuggestion()
            debugState.recordSuggestionResult("Suggestion completed by typing", visibleSuggestion = false)
            return true
        }

        val advancedSuggestion =
            suggestion.copy(
                modificationStamp = event.document.modificationStamp,
                startOffset = update.startOffset,
                endOffset = update.endOffset,
                originalText = update.currentText,
                caretOffset = suggestion.editor.caretModel.offset,
            )
        currentPresentedSuggestion = presenter.show(advancedSuggestion)
        val presentation = currentPresentedSuggestion?.presentationDescription
        debugState.recordSuggestionResult(
            message =
                if (currentPresentedSuggestion == null) {
                    "Suggestion consumed by typing; no changed block remains"
                } else {
                    "Suggestion advanced by matching typed edit: $presentation"
                },
            visibleSuggestion = currentPresentedSuggestion != null,
        )
        return true
    }

    private fun handleModifierKeyEvent(event: AWTEvent) {
        val altPressed =
            when (event) {
                is KeyEvent -> altPressedAfter(event) ?: return
                is WindowEvent ->
                    when (event.id) {
                        WindowEvent.WINDOW_DEACTIVATED,
                        WindowEvent.WINDOW_LOST_FOCUS,
                        -> false
                        else -> return
                    }
                else -> return
            }

        setAltGhostTextOverride(altPressed)
    }

    private fun altPressedAfter(event: KeyEvent): Boolean? =
        when (event.id) {
            KeyEvent.KEY_PRESSED ->
                if (event.keyCode == KeyEvent.VK_ALT_GRAPH) {
                    false
                } else if (event.keyCode == KeyEvent.VK_ALT) {
                    true
                } else {
                    event.hasPlainAltModifier()
                }
            KeyEvent.KEY_RELEASED ->
                if (event.keyCode == KeyEvent.VK_ALT || event.keyCode == KeyEvent.VK_ALT_GRAPH) {
                    false
                } else {
                    event.hasPlainAltModifier()
                }
            else -> null
        }

    private fun KeyEvent.hasPlainAltModifier(): Boolean =
        (modifiersEx and InputEvent.ALT_DOWN_MASK) != 0 &&
            (modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK) == 0

    private fun setAltGhostTextOverride(active: Boolean) {
        if (altGhostTextOverride == active) return
        altGhostTextOverride = active
        presenter.setGhostTextOverride(active)

        if (FreddieSettings.getInstance().suggestionDisplayMode != FreddieSuggestionDisplayMode.LINE_HINT) return
        repaintVisibleSuggestionForDisplayModeOverride(
            if (active) {
                "Alt pressed; temporarily showing line hint suggestion as ghost text"
            } else {
                "Alt released; restored line hint suggestion display"
            },
        )
    }

    private fun repaintVisibleSuggestionForDisplayModeOverride(message: String) {
        val suggestion = validCurrentSuggestionForRepaint() ?: return
        currentPresentedSuggestion = presenter.show(suggestion)
        val presentation = currentPresentedSuggestion?.presentationDescription
        debugState.recordSuggestionResult(
            message =
                if (presentation != null) {
                    "$message: $presentation"
                } else {
                    "$message, but the suggestion was not rendered"
                },
            visibleSuggestion = currentPresentedSuggestion != null,
        )
    }

    private fun validCurrentSuggestionForRepaint(): MercurySuggestion? {
        val suggestion = currentPresentedSuggestion?.suggestion ?: return null
        val invalidReason =
            when {
                suggestion.editor.isDisposed -> "editor was disposed"
                project.isDisposed -> "project was disposed"
                suggestion.editor.project != project -> "editor no longer belongs to this project"
                else -> null
            }
        if (invalidReason != null) {
            clearVisibleSuggestion()
            debugState.recordSuggestionResult(
                "Suggestion preview cleared before repaint: $invalidReason",
                visibleSuggestion = false,
            )
            return null
        }
        return suggestion
    }

    private fun recordUserEdit(
        document: Document,
        oldText: String,
        newText: String,
    ) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        recordEditHistory(projectRelativePath(project, file), oldText, newText)
    }

    private fun recordEditHistory(
        filePath: String,
        oldText: String,
        newText: String,
    ) {
        recentEditHistory.recordEdit(filePath, oldText, newText)
        debugState.recordRecentEditDiffs(recentEditHistory.formattedDiffsWithinBudget().diffsOldestToNewest)
    }

    private fun handleRequestError(
        requestId: Long,
        triggerKind: TriggerKind,
        error: Throwable,
    ) {
        if (requestId != requestSerial.get() || project.isDisposed) {
            debugState.recordEvent("Ignored stale Mercury error for request #$requestId")
            return
        }
        currentTask = null
        debugState.recordError(triggerKind, error)

        if (triggerKind == TriggerKind.MANUAL && shouldNotify(error)) {
            notifyUser("Mercury request failed", error.message ?: "Unknown Mercury error")
        } else {
            LOG.debug("Mercury next edit request failed", error)
        }
    }

    private fun shouldNotify(error: Throwable): Boolean =
        error !is MercuryApiException || error.shouldNotifyUser

    private fun describeLineSeparator(lineSeparator: String): String =
        when (lineSeparator) {
            "\r\n" -> "CRLF"
            "\n" -> "LF"
            "\r" -> "CR"
            else -> "the document line separator"
        }

    private fun resolveSnapshotRegion(snapshot: MercuryRequestSnapshot): RegionCheck {
        if (snapshot.editor.isDisposed) return RegionCheck(discardReason = "editor was disposed")
        if (snapshot.document.modificationStamp != snapshot.modificationStamp) {
            return RegionCheck(
                discardReason =
                    "document modification stamp changed: expected ${snapshot.modificationStamp}, " +
                        "actual ${snapshot.document.modificationStamp}",
            )
        }
        return resolveEditableRegion(
            document = snapshot.document,
            expectedStartOffset = snapshot.editableRegion.startOffset,
            expectedEndOffset = snapshot.editableRegion.endOffset,
            originalText = snapshot.editableRegion.originalText,
        )
    }

    private fun resolveSuggestionRegion(suggestion: MercurySuggestion): RegionCheck {
        if (suggestion.editor.isDisposed) return RegionCheck(discardReason = "editor was disposed")
        if (suggestion.document.modificationStamp != suggestion.modificationStamp) {
            return RegionCheck(
                discardReason =
                    "document modification stamp changed: expected ${suggestion.modificationStamp}, " +
                        "actual ${suggestion.document.modificationStamp}",
            )
        }
        return resolveEditableRegion(
            document = suggestion.document,
            expectedStartOffset = suggestion.startOffset,
            expectedEndOffset = suggestion.endOffset,
            originalText = suggestion.originalText,
        )
    }

    private fun resolveEditableRegion(
        document: Document,
        expectedStartOffset: Int,
        expectedEndOffset: Int,
        originalText: String,
    ): RegionCheck {
        val resolution =
            EditableRegionMatcher.resolve(
                documentText = document.text,
                expectedStartOffset = expectedStartOffset,
                expectedEndOffset = expectedEndOffset,
                originalText = originalText,
            )
        val match = resolution.match
        return if (match != null) {
            RegionCheck(
                region =
                    ResolvedSuggestionRegion(
                        startOffset = match.startOffset,
                        endOffset = match.endOffset,
                        note = match.note,
                    ),
            )
        } else {
            RegionCheck(discardReason = resolution.failureReason ?: "editable region could not be matched")
        }
    }

    private fun editorFor(document: Document): Editor? =
        EditorFactory
            .getInstance()
            .allEditors
            .firstOrNull { it.project == project && it.document == document && !it.isDisposed }

    private fun recordEditorVisit(editor: Editor) {
        if (!editor.isDisposed && editor.project == project) {
            recentlyViewedSnippetTracker.record(editor)
            trackingFor(editor.document).lastEditor = editor
        }
    }

    private fun invalidatePendingRequests() {
        invalidateActiveRequest()
        typedRequestAlarm.cancelAllRequests()
    }

    private fun invalidateActiveRequest() {
        requestSerial.incrementAndGet()
        currentTask?.cancel(true)
        currentTask = null
    }

    private fun clearVisibleSuggestion() {
        currentPresentedSuggestion = null
        presenter.dispose()
    }

    private fun clearVisibleSuggestionIfOwnedBy(editor: Editor) {
        if (currentPresentedSuggestion?.suggestion?.editor !== editor) return
        clearVisibleSuggestion()
        debugState.recordSuggestionResult("Suggestion preview cleared because its editor was released", visibleSuggestion = false)
    }

    private fun notifyUser(
        title: String,
        message: String,
        type: NotificationType = NotificationType.ERROR,
    ) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(title, message, type)
            .notify(project)
    }

    override fun dispose() {
        invalidatePendingRequests()
        clearVisibleSuggestion()
        editorDisposables.values.forEach { Disposer.dispose(it) }
        editorDisposables.clear()
        documentTrackings.values.forEach { Disposer.dispose(it.disposable) }
        documentTrackings.clear()
    }

    companion object {
        private val LOG = Logger.getInstance(MercuryNextEditController::class.java)
        private const val NOTIFICATION_GROUP = "Freddie Mercury"
    }
}
