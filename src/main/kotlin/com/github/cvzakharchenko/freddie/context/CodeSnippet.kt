package com.github.cvzakharchenko.freddie.context

data class CodeSnippet(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val text: String,
    val timestamp: Long,
)

data class SnippetSelection(
    val snippets: List<CodeSnippet>,
    val budget: ContextBudgetDebugInfo,
)

internal fun CodeSnippet.promptCharCost(): Int = text.length + filePath.length + SNIPPET_PROMPT_OVERHEAD_CHARS

private const val SNIPPET_PROMPT_OVERHEAD_CHARS = 128
