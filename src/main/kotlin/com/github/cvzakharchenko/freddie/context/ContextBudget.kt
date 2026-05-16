package com.github.cvzakharchenko.freddie.context

object ContextBudget {
    const val ESTIMATED_CHARS_PER_TOKEN = 4

    const val CURRENT_FILE_TOKENS = 15_000
    const val RECENT_EDITS_TOKENS = 5_000
    const val RECENT_SNIPPETS_TOKENS = 3_000
    const val RECENT_COPIED_SNIPPETS_TOKENS = 3_000

    const val CURRENT_FILE_CHARS = CURRENT_FILE_TOKENS * ESTIMATED_CHARS_PER_TOKEN
    const val RECENT_EDITS_CHARS = RECENT_EDITS_TOKENS * ESTIMATED_CHARS_PER_TOKEN
    const val RECENT_SNIPPETS_CHARS = RECENT_SNIPPETS_TOKENS * ESTIMATED_CHARS_PER_TOKEN
    const val RECENT_COPIED_SNIPPETS_CHARS = RECENT_COPIED_SNIPPETS_TOKENS * ESTIMATED_CHARS_PER_TOKEN
}

data class ContextBudgetDebugInfo(
    val budgetTokens: Int,
    val budgetChars: Int,
    val usedChars: Int,
    val droppedChars: Int,
    val keptItems: Int,
    val droppedItems: Int,
)
