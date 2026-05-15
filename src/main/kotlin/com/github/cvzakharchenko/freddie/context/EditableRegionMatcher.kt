package com.github.cvzakharchenko.freddie.context

data class EditableRegionMatch(
    val startOffset: Int,
    val endOffset: Int,
    val note: String? = null,
)

data class EditableRegionResolution(
    val match: EditableRegionMatch?,
    val failureReason: String?,
)

object EditableRegionMatcher {
    fun resolve(
        documentText: String,
        expectedStartOffset: Int,
        expectedEndOffset: Int,
        originalText: String,
    ): EditableRegionResolution {
        val expectedOffsetsAreValid =
            expectedStartOffset >= 0 &&
                expectedEndOffset >= expectedStartOffset &&
                expectedEndOffset <= documentText.length

        if (expectedOffsetsAreValid) {
            val currentText = documentText.substring(expectedStartOffset, expectedEndOffset)
            if (currentText == originalText) {
                return EditableRegionResolution(
                    match = EditableRegionMatch(expectedStartOffset, expectedEndOffset),
                    failureReason = null,
                )
            }
        }

        val relocatedStart = findNearestOccurrence(documentText, originalText, expectedStartOffset)
        if (relocatedStart != null) {
            val relocatedEnd = relocatedStart + originalText.length
            return EditableRegionResolution(
                match =
                    EditableRegionMatch(
                        startOffset = relocatedStart,
                        endOffset = relocatedEnd,
                        note =
                            "editable region matched by text at $relocatedStart-$relocatedEnd; " +
                                "saved offsets were $expectedStartOffset-$expectedEndOffset, document length ${documentText.length}",
                    ),
                failureReason = null,
            )
        }

        val reason =
            if (expectedOffsetsAreValid) {
                "editable region text changed at saved offsets $expectedStartOffset-$expectedEndOffset; " +
                    "document length ${documentText.length}, original length ${originalText.length}"
            } else {
                "editable region offsets are invalid: start=$expectedStartOffset, end=$expectedEndOffset, " +
                    "document length ${documentText.length}, original length ${originalText.length}"
            }
        return EditableRegionResolution(match = null, failureReason = "$reason; original text was not found elsewhere")
    }

    private fun findNearestOccurrence(
        documentText: String,
        originalText: String,
        expectedStartOffset: Int,
    ): Int? {
        if (originalText.isEmpty() || originalText.length > documentText.length) return null

        val searchRadius = maxOf(2_000, originalText.length * 2)
        val windowStart = (expectedStartOffset - searchRadius).coerceAtLeast(0)
        val windowEnd = (expectedStartOffset + searchRadius + originalText.length).coerceAtMost(documentText.length)
        val windowMatch = nearestOccurrenceInRange(documentText, originalText, expectedStartOffset, windowStart, windowEnd)
        if (windowMatch != null) return windowMatch

        return nearestOccurrenceInRange(documentText, originalText, expectedStartOffset, 0, documentText.length)
    }

    private fun nearestOccurrenceInRange(
        documentText: String,
        originalText: String,
        expectedStartOffset: Int,
        rangeStart: Int,
        rangeEnd: Int,
    ): Int? {
        if (rangeEnd - rangeStart < originalText.length) return null

        var bestMatch: Int? = null
        var bestDistance = Int.MAX_VALUE
        var fromIndex = rangeStart
        while (fromIndex <= rangeEnd - originalText.length) {
            val match = documentText.indexOf(originalText, fromIndex)
            if (match < 0 || match + originalText.length > rangeEnd) break
            val distance = kotlin.math.abs(match - expectedStartOffset)
            if (distance < bestDistance) {
                bestMatch = match
                bestDistance = distance
            }
            fromIndex = match + 1
        }
        return bestMatch
    }
}
