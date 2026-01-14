package com.pace.legends.domain.model

import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState() // 0-100
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
