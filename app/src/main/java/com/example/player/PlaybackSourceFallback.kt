package com.example.player

internal object PlaybackSourceFallback {
    fun nextUntriedSource(sourceCount: Int, attemptedIndexes: Set<Int>): Int? =
        (0 until sourceCount).firstOrNull { it !in attemptedIndexes }
}
