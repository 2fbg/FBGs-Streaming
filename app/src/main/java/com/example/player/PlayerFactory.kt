package com.example.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

object PlayerFactory {
    fun createFastStartPlayer(context: Context): ExoPlayer {
        return com.fbg.streaming.player.Mk21PlayerFactory.create(
            context = context,
            profile = com.fbg.streaming.player.Mk21PlayerFactory.PlaybackProfile.LIVE_FAST_ZAPPING
        )
    }

    fun createVodPlayer(context: Context): ExoPlayer {
        return com.fbg.streaming.player.Mk21PlayerFactory.create(
            context = context,
            profile = com.fbg.streaming.player.Mk21PlayerFactory.PlaybackProfile.VOD_CINEMA_STABLE
        )
    }
}
