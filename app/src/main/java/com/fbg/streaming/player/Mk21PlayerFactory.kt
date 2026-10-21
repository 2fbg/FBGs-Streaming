package com.fbg.streaming.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor

@OptIn(UnstableApi::class)
object Mk21PlayerFactory {

   enum class PlaybackProfile {
       LIVE_FAST_ZAPPING, // Troca rápida de canais ao vivo
       VOD_CINEMA_STABLE  // Filmes e séries com buffer longo
   }

   fun create(
       context: Context,
       profile: PlaybackProfile = PlaybackProfile.LIVE_FAST_ZAPPING
   ): ExoPlayer {
       // 1. Renderers com fallback automático para Software Decoder quando o codec da TV Box falha
       val renderersFactory = DefaultRenderersFactory(context)
           .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
           .setEnableDecoderFallback(true)

       // 2. Extrator MPEG-TS otimizado para Zapping Rápido (não trava esperando keyframe IDR perfeito)
       val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
           DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS

       val extractorsFactory = DefaultExtractorsFactory()
           .setTsExtractorFlags(tsFlags)
           .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

       // 3. LoadControl adaptativo por perfil (Live TV vs VOD)
       val loadControl = when (profile) {
           PlaybackProfile.LIVE_FAST_ZAPPING -> DefaultLoadControl.Builder()
               .setBufferDurationsMs(
                   /* minBufferMs = */ 8_000,
                   /* maxBufferMs = */ 25_000,
                   /* bufferForPlaybackMs = */ 800,
                   /* bufferForPlaybackAfterRebufferMs = */ 1_800
               )
               .setPrioritizeTimeOverSizeThresholds(true)
               .build()

           PlaybackProfile.VOD_CINEMA_STABLE -> DefaultLoadControl.Builder()
               .setBufferDurationsMs(
                   /* minBufferMs = */ 20_000,
                   /* maxBufferMs = */ 60_000,
                   /* bufferForPlaybackMs = */ 1_500,
                   /* bufferForPlaybackAfterRebufferMs = */ 3_000
               )
               .setPrioritizeTimeOverSizeThresholds(false)
               .build()
       }

       val httpDataSourceFactory = DefaultHttpDataSource.Factory()
           .setUserAgent("MK21-MultiServidor/2.1 (Android; ExoPlayer)")
           .setAllowCrossProtocolRedirects(true)
           .setConnectTimeoutMs(8_000)
           .setReadTimeoutMs(15_000)

       val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
           .setDataSourceFactory(httpDataSourceFactory)

       return ExoPlayer.Builder(context, renderersFactory)
           .setMediaSourceFactory(mediaSourceFactory)
           .setLoadControl(loadControl)
           .setAudioAttributes(
               AudioAttributes.Builder()
                   .setUsage(C.USAGE_MEDIA)
                   .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                   .build(),
               true
           )
           .setHandleAudioBecomingNoisy(true)
           .build()
   }

   fun buildLiveMediaItem(proxyStreamUrl: String): MediaItem {
       return MediaItem.Builder()
           .setUri(proxyStreamUrl)
           .setLiveConfiguration(
               MediaItem.LiveConfiguration.Builder()
                   .setTargetOffsetMs(3_000)
                   .setMinPlaybackSpeed(0.97f)
                   .setMaxPlaybackSpeed(1.04f)
                   .build()
           )
           .build()
   }
}