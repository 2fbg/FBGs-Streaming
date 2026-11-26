package com.example.data.service

import android.content.Context
import com.example.data.model.PlaylistItem

/**
 * Facade delegating to modularized Cast components (LocalHttpServer, DlnaDiscoveryService, DlnaSoapClient)
 * while maintaining active session state across dialog re-opens.
 */
object LocalCastServer {
    val httpServer = LocalHttpServer()
    val discovery = DlnaDiscoveryService()
    val soapClient = DlnaSoapClient()

    var activeItem: PlaylistItem?
        get() = httpServer.activeItem
        set(value) { httpServer.activeItem = value }

    var serverPort: Int
        get() = httpServer.serverPort
        set(value) { httpServer.serverPort = value }

    val isRunning: Boolean
        get() = httpServer.isRunning

    var remoteIsPlaying: Boolean
        get() = httpServer.remoteIsPlaying
        set(value) { httpServer.remoteIsPlaying = value }

    var remoteSeekRequest: Long
        get() = httpServer.remoteSeekRequest
        set(value) { httpServer.remoteSeekRequest = value }

    var remoteVolume: Float
        get() = httpServer.remoteVolume
        set(value) { httpServer.remoteVolume = value }

    var tvCurrentTimeSeconds: Float
        get() = httpServer.tvCurrentTimeSeconds
        set(value) { httpServer.tvCurrentTimeSeconds = value }

    @Volatile
    var pairedDevice: DLNADevice? = null

    @Volatile
    var useLocalProxyForDlna: Boolean = true

    var dlnaControlUrl: String?
        get() = pairedDevice?.controlUrl
        set(value) {
            if (value == null) {
                pairedDevice = null
            } else if (pairedDevice == null) {
                pairedDevice = DLNADevice(
                    friendlyName = "Smart TV",
                    controlUrl = value,
                    baseUrl = "",
                    ipAddress = dlnaDeviceIp.orEmpty()
                )
            }
        }

    var dlnaDeviceIp: String? = null

    val dlnaRenderingControlUrl: String?
        get() = pairedDevice?.renderingControlUrl

    fun startServer(context: Context, item: PlaylistItem): String? =
        httpServer.startServer(context, item)

    fun stopServer() {
        pairedDevice?.controlUrl?.let { soapClient.stopDlna(it) }
        pairedDevice = null
        dlnaDeviceIp = null
        httpServer.stopServer()
    }

    fun disconnectDlna() {
        pairedDevice?.controlUrl?.let { soapClient.stopDlna(it) }
        pairedDevice = null
        dlnaDeviceIp = null
    }

    fun getLocalIpAddress(context: Context): String =
        httpServer.getLocalIpAddress(context)

    fun getCastUrl(context: Context): String =
        httpServer.getCastUrl(context)

    fun getStreamProxyUrl(context: Context, item: PlaylistItem): String =
        httpServer.getStreamProxyUrl(context, item)

    fun discoverDlnaDevices(onDeviceDiscovered: (DLNADevice) -> Unit) =
        discovery.discoverDlnaDevices(onDeviceDiscovered)

    fun discoverDlnaDevices(
        context: Context?,
        onDeviceDiscovered: (DLNADevice) -> Unit,
        onScanComplete: (() -> Unit)? = null
    ) = discovery.discoverDlnaDevices(context, onDeviceDiscovered, onScanComplete)

    fun probeManualDevice(ip: String, onResult: (Boolean, DLNADevice?) -> Unit) =
        discovery.probeManualDevice(ip, onResult)

    fun castUrlToDlna(controlUrl: String, streamUrl: String, title: String) =
        soapClient.castUrlToDlna(controlUrl, streamUrl, title)

    fun castUrlToDlna(
        controlUrl: String,
        streamUrl: String,
        title: String,
        contentType: String = "LIVE",
        onResult: ((DlnaCastResult) -> Unit)? = null
    ) = soapClient.castUrlToDlna(controlUrl, streamUrl, title, contentType, onResult)

    fun pauseDlna(controlUrl: String) =
        soapClient.pauseDlna(controlUrl)

    fun resumeDlna(controlUrl: String) =
        soapClient.resumeDlna(controlUrl)

    fun seekDlna(controlUrl: String, positionSeconds: Long) =
        soapClient.seekDlna(controlUrl, positionSeconds)

    fun stopDlna(controlUrl: String) =
        soapClient.stopDlna(controlUrl)

    fun setVolumeDlna(renderingControlUrl: String, volumePercent: Int) =
        soapClient.setVolumeDlna(renderingControlUrl, volumePercent)
}
