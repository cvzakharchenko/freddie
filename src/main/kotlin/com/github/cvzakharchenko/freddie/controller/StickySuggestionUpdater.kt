package com.github.cvzakharchenko.freddie.controller

internal data class StickySuggestionUpdate(
    val startOffset: Int,
    val endOffset: Int,
    val currentText: String,
    val completed: Boolean,
)

internal object StickySuggestionUpdater {
    fun advance(
        oldDocumentText: String,
        newDocumentText: String,
        regionStartOffset: Int,
        regionEndOffset: Int,
        currentRegionText: String,
        replacementText: String,
        editOffset: Int,
        oldLength: Int,
        newLength: Int,
    ): StickySuggestionUpdate? {
        if (newLength <= oldLength) return null
        if (regionStartOffset < 0 || regionEndOffset < regionStartOffset || regionEndOffset > oldDocumentText.length) {
            return null
        }
        if (oldDocumentText.substring(regionStartOffset, regionEndOffset) != currentRegionText) return null

        val editOldEnd = editOffset + oldLength
        val editTouchesRegion =
            if (oldLength == 0) {
                editOffset in regionStartOffset..regionEndOffset
            } else {
                editOffset < regionEndOffset && editOldEnd > regionStartOffset
            }
        if (!editTouchesRegion) return null

        val delta = newLength - oldLength
        val editBeforeRegion = editOldEnd <= regionStartOffset && editOffset < regionStartOffset
        val newStartOffset =
            when {
                editBeforeRegion -> regionStartOffset + delta
                editOffset >= regionStartOffset -> regionStartOffset
                else -> return null
            }
        val newEndOffset =
            when {
                editBeforeRegion -> regionEndOffset + delta
                editOffset <= regionEndOffset -> regionEndOffset + delta
                else -> regionEndOffset
            }
        if (newStartOffset < 0 || newEndOffset < newStartOffset || newEndOffset > newDocumentText.length) return null

        val newRegionText = newDocumentText.substring(newStartOffset, newEndOffset)
        if (!isReplacementWithOneGap(current = newRegionText, replacement = replacementText)) return null

        return StickySuggestionUpdate(
            startOffset = newStartOffset,
            endOffset = newEndOffset,
            currentText = newRegionText,
            completed = newRegionText == replacementText,
        )
    }

    private fun isReplacementWithOneGap(
        current: String,
        replacement: String,
    ): Boolean {
        if (current.length > replacement.length) return false

        var prefix = 0
        while (
            prefix < current.length &&
            prefix < replacement.length &&
            current[prefix] == replacement[prefix]
        ) {
            prefix++
        }

        var suffix = 0
        while (
            suffix < current.length - prefix &&
            suffix < replacement.length - prefix &&
            current[current.lastIndex - suffix] == replacement[replacement.lastIndex - suffix]
        ) {
            suffix++
        }

        return prefix + suffix == current.length
    }
}
