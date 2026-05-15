package com.github.cvzakharchenko.freddie.presentation

import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer

internal data class ChangedSlice(
    val originalStartOffset: Int,
    val originalEndOffset: Int,
    val originalChange: String,
    val replacementChange: String,
) {
    val isEmpty: Boolean
        get() = originalChange.isEmpty() && replacementChange.isEmpty()

    companion object {
        fun between(
            original: String,
            replacement: String,
        ): ChangedSlice {
            return betweenNormalized(
                normalizedOriginal = original,
                originalSourceOffset = { it },
                originalSourceText = original,
                normalizedReplacement = replacement,
            )
        }

        fun betweenIgnoringLineEndings(
            original: String,
            replacement: String,
        ): ChangedSlice {
            val normalizedOriginal = LineEndingNormalizer.normalizeToLfWithSourceOffsets(original)
            val normalizedReplacement = LineEndingNormalizer.normalizeToLf(replacement)
            return betweenNormalized(
                normalizedOriginal = normalizedOriginal.text,
                originalSourceOffset = normalizedOriginal::sourceOffset,
                originalSourceText = original,
                normalizedReplacement = normalizedReplacement,
            )
        }

        private fun betweenNormalized(
            normalizedOriginal: String,
            originalSourceOffset: (Int) -> Int,
            originalSourceText: String,
            normalizedReplacement: String,
        ): ChangedSlice {
            var prefix = 0
            val maxPrefix = minOf(normalizedOriginal.length, normalizedReplacement.length)
            while (prefix < maxPrefix && normalizedOriginal[prefix] == normalizedReplacement[prefix]) {
                prefix++
            }

            var suffix = 0
            while (
                suffix < normalizedOriginal.length - prefix &&
                suffix < normalizedReplacement.length - prefix &&
                normalizedOriginal[normalizedOriginal.lastIndex - suffix] == normalizedReplacement[normalizedReplacement.lastIndex - suffix]
            ) {
                suffix++
            }

            val originalStartOffset = originalSourceOffset(prefix)
            val originalEndOffset = originalSourceOffset(normalizedOriginal.length - suffix)
            return ChangedSlice(
                originalStartOffset = originalStartOffset,
                originalEndOffset = originalEndOffset,
                originalChange = originalSourceText.substring(originalStartOffset, originalEndOffset),
                replacementChange = normalizedReplacement.substring(prefix, normalizedReplacement.length - suffix),
            )
        }
    }
}
