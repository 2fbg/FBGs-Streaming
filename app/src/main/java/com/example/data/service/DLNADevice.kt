package com.example.data.service

data class DLNADevice(
    val friendlyName: String,
    val controlUrl: String,
    val baseUrl: String,
    val ipAddress: String,
    val renderingControlUrl: String? = null,
    val manufacturer: String = "",
    val modelName: String = ""
)
