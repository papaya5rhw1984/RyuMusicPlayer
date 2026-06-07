package com.ryu.musicplayer.data

import android.net.Uri

/** 기기에서 스캔한 음악 한 곡 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri?,   // 앨범 아트 (없으면 null)
    val folder: String      // 파일이 속한 폴더명 (자동 그룹용)
)
