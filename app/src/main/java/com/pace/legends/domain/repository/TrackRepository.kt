package com.pace.legends.domain.repository

import com.pace.legends.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface TrackRepository {
    suspend fun getTracks(): Result<List<Track>>
    suspend fun getTrack(trackId: String): Result<Track?>
    fun prepareTrackFile(track: Track): kotlinx.coroutines.flow.Flow<com.pace.legends.domain.model.DownloadState>
}
