package com.github.cvzakharchenko.freddie.debug

import com.github.cvzakharchenko.freddie.context.MercuryContextDebugInfo
import com.github.cvzakharchenko.freddie.context.MercuryRequestSnapshot
import com.github.cvzakharchenko.freddie.mercury.MercuryApiException
import com.github.cvzakharchenko.freddie.mercury.MercuryCompletion
import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.github.cvzakharchenko.freddie.settings.MercuryApiKeyStore
import com.github.cvzakharchenko.freddie.trigger.MercuryTriggerDecision
import com.github.cvzakharchenko.freddie.trigger.TriggerKind
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.EventListener

data class FreddieDebugSnapshot(
    val connectionStatus: String = "Idle",
    val enabled: Boolean = FreddieSettings.getInstance().nextEditEnabled,
    val debounceMs: Int = FreddieSettings.getInstance().debounceMs,
    val apiKeySource: String = MercuryApiKeyStore.describeApiKeySource(),
    val visibleSuggestion: Boolean = false,
    val lastTrigger: String = "none",
    val lastDecision: String = "No trigger decision yet.",
    val lastEvent: String = "No activity yet.",
    val context: MercuryContextDebugInfo? = null,
    val lastPrompt: String = "",
    val lastCodeToEdit: String = "",
    val recentEditDiffsOldestToNewest: List<String> = emptyList(),
    val lastResponseSummary: String = "No response yet.",
    val lastResponseText: String = "",
    val lastRawResponseBody: String = "",
    val responseRevision: Long = 0,
    val lastError: String = "",
    val events: List<String> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

interface FreddieDebugListener : EventListener {
    fun snapshotChanged(snapshot: FreddieDebugSnapshot)
}

@Service(Service.Level.PROJECT)
class FreddieDebugStateService(
    @Suppress("UNUSED_PARAMETER") private val project: Project,
) {
    private val dispatcher = EventDispatcher.create<FreddieDebugListener>(FreddieDebugListener::class.java)

    @Volatile
    private var snapshot = FreddieDebugSnapshot()

    fun currentSnapshot(): FreddieDebugSnapshot = snapshot

    fun addListener(
        listener: FreddieDebugListener,
        parentDisposable: Disposable,
    ) {
        dispatcher.addListener(listener, parentDisposable)
        listener.snapshotChanged(snapshot)
    }

    fun clear() {
        publish(FreddieDebugSnapshot().withEvent("Debug panel cleared"))
    }

    fun recordStartup() {
        update("Controller started") {
            it.copy(
                connectionStatus = "Idle",
                enabled = FreddieSettings.getInstance().nextEditEnabled,
                debounceMs = FreddieSettings.getInstance().debounceMs,
                apiKeySource = MercuryApiKeyStore.describeApiKeySource(),
            )
        }
    }

    fun recordEvent(message: String) {
        update(message) { it }
    }

    fun recordTypedDecision(
        decision: MercuryTriggerDecision,
        debounceMs: Int,
    ) {
        update("Typed edit: ${decision.reason}") {
            it.copy(
                connectionStatus = if (decision.shouldRequest) "Debouncing typed edit" else "Idle",
                debounceMs = debounceMs,
                lastTrigger = TriggerKind.TYPED_EDIT.name,
                lastDecision = if (decision.shouldRequest) "Will request after ${debounceMs}ms: ${decision.reason}" else "Skipped: ${decision.reason}",
            )
        }
    }

    fun recordRequestSkipped(
        triggerKind: TriggerKind,
        reason: String,
    ) {
        update("${triggerKind.name} request skipped: $reason") {
            it.copy(
                connectionStatus = "Idle",
                enabled = FreddieSettings.getInstance().nextEditEnabled,
                debounceMs = FreddieSettings.getInstance().debounceMs,
                apiKeySource = MercuryApiKeyStore.describeApiKeySource(),
                lastTrigger = triggerKind.name,
                lastDecision = "Skipped: $reason",
            )
        }
    }

    fun recordRequestStarted(
        triggerKind: TriggerKind,
        requestNumber: Long,
        requestSnapshot: MercuryRequestSnapshot,
        visibleSuggestion: Boolean = false,
    ) {
        update("${triggerKind.name} request #$requestNumber started") {
            it.copy(
                connectionStatus = "Requesting Mercury",
                enabled = FreddieSettings.getInstance().nextEditEnabled,
                debounceMs = FreddieSettings.getInstance().debounceMs,
                apiKeySource = MercuryApiKeyStore.describeApiKeySource(),
                visibleSuggestion = visibleSuggestion,
                lastTrigger = triggerKind.name,
                lastDecision = "Request #$requestNumber sent to Mercury",
                context = requestSnapshot.debugInfo,
                lastPrompt = requestSnapshot.prompt,
                lastCodeToEdit = requestSnapshot.debugInfo.codeToEditBlock,
                recentEditDiffsOldestToNewest = requestSnapshot.debugInfo.editDiffsOldestToNewest,
                lastResponseSummary = "Waiting for Mercury response...",
                lastResponseText = "",
                lastRawResponseBody = "",
                responseRevision = it.responseRevision + 1,
                lastError = "",
            )
        }
    }

    fun recordResponse(completion: MercuryCompletion) {
        val summary =
            buildString {
                append("HTTP ")
                append(completion.statusCode ?: "?")
                completion.elapsedMs?.let { append(" in ${it}ms") }
                completion.requestId?.let { append(", request id $it") }
                append(", replacement ")
                append(completion.replacementText?.length ?: 0)
                append(" chars")
            }
        update("Mercury response received") {
            it.copy(
                connectionStatus = "Mercury responded",
                lastResponseSummary = summary,
                lastResponseText = completion.rawText ?: completion.replacementText.orEmpty(),
                lastRawResponseBody = completion.responseBody.orEmpty(),
                responseRevision = it.responseRevision + 1,
                lastError = "",
            )
        }
    }

    fun recordRecentEditDiffs(diffsOldestToNewest: List<String>) {
        publish(
            snapshot.copy(
                recentEditDiffsOldestToNewest = diffsOldestToNewest,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun recordSuggestionResult(
        message: String,
        visibleSuggestion: Boolean,
    ) {
        update(message) {
            it.copy(
                connectionStatus = if (visibleSuggestion) "Suggestion visible" else "Idle",
                visibleSuggestion = visibleSuggestion,
                lastDecision = message,
            )
        }
    }

    fun recordDebugPreviewResult(
        message: String,
        visibleSuggestion: Boolean,
    ) {
        update(message) {
            it.copy(
                connectionStatus = if (visibleSuggestion) "Debug preview visible" else "Idle",
                visibleSuggestion = visibleSuggestion,
                lastDecision = message,
            )
        }
    }

    fun recordError(
        triggerKind: TriggerKind,
        error: Throwable,
    ) {
        val errorText =
            if (error is MercuryApiException) {
                buildString {
                    append("HTTP ${error.statusCode}: ${error.message}")
                    error.responseBody?.takeIf { it.isNotBlank() }?.let {
                        append("\n\n")
                        append(it)
                    }
                }
            } else {
                "${error::class.java.simpleName}: ${error.message ?: "unknown error"}"
            }
        update("${triggerKind.name} request failed") {
            it.copy(
                connectionStatus = "Error",
                lastTrigger = triggerKind.name,
                lastDecision = "Request failed",
                lastError = errorText,
            )
        }
    }

    private fun update(
        event: String,
        transform: (FreddieDebugSnapshot) -> FreddieDebugSnapshot,
    ) {
        publish(transform(snapshot).withEvent(event))
    }

    private fun publish(nextSnapshot: FreddieDebugSnapshot) {
        snapshot = nextSnapshot
        dispatcher.multicaster.snapshotChanged(nextSnapshot)
    }

    private fun FreddieDebugSnapshot.withEvent(event: String): FreddieDebugSnapshot {
        val now = System.currentTimeMillis()
        val eventLine = "${LocalTime.now().format(TIME_FORMAT)}  $event"
        return copy(
            lastEvent = event,
            events = (events + eventLine).takeLast(MAX_EVENTS),
            updatedAtMillis = now,
        )
    }

    companion object {
        private const val MAX_EVENTS = 120
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
