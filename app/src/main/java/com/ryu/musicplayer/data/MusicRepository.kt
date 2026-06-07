package com.ryu.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * MediaStore를 통해 기기에 저장된 음악 파일을 조회한다.
 * (HTML에서 로컬 파일을 읽던 것을 네이티브 방식으로 대체)
 */
object MusicRepository {

    private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

    fun loadTracks(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // 실제 음악 파일만 (벨소리/알림음 제외)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val albumId = cursor.getLong(albumIdCol)
                val artUri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

                tracks += Track(
                    id = id,
                    title = cursor.getString(titleCol) ?: "제목 없음",
                    artist = cursor.getString(artistCol) ?: "알 수 없는 아티스트",
                    album = cursor.getString(albumCol) ?: "",
                    durationMs = cursor.getLong(durationCol),
                    uri = uri,
                    artworkUri = artUri,
                    folder = folderNameOf(cursor.getString(dataCol))
                )
            }
        }
        return tracks
    }

    /** /storage/emulated/0/Music/Kpop/song.mp3 -> "Kpop" */
    private fun folderNameOf(path: String?): String {
        if (path.isNullOrBlank()) return "내 음악"
        val parts = path.trimEnd('/').split('/')
        return if (parts.size >= 2) parts[parts.size - 2] else "내 음악"
    }
}
