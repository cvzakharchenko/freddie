package com.github.cvzakharchenko.freddie.presentation

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor

data class MercurySuggestion(
    val editor: Editor,
    val document: Document,
    val filePath: String,
    val modificationStamp: Long,
    val caretOffset: Int,
    val startOffset: Int,
    val endOffset: Int,
    val originalText: String,
    val replacementText: String,
)

interface PresentedSuggestion : Disposable {
    val suggestion: MercurySuggestion
    val presentationDescription: String
        get() = "unknown presentation"
}

interface SuggestionPresenter : Disposable {
    fun show(suggestion: MercurySuggestion): PresentedSuggestion?
}
