package com.ryu.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.ryu.musicplayer.data.MusicRepository
import com.ryu.musicplayer.data.PlaylistStore
import com.ryu.musicplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ALL_CHIP = "전체"
val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.5f)

data class PlayerUiState(
    val allTracks: List<Track> = emptyList(),
    val folders: List<String> = emptyList(),               // 자동 폴더 그룹
    val playlists: List<String> = emptyList(),             // 사용자 재생목록 이름
    val playlistMembers: Map<String, Set<Long>> = emptyMap(),
    val playlistRevision: Int = 0,                          // 목록 변경 감지용(Set 순서 무시 문제 방지)
    val activeChip: String = ALL_CHIP,
    val query: String = "",
    val isLoading: Boolean = false,
    // 재생 상태
    val currentTrackId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speedIndex: Int = 0,
    val volume: Float = 1f
) {
    val currentTrack: Track? get() = allTracks.firstOrNull { it.id == currentTrackId }
    val speed: Float get() = SPEEDS[speedIndex]
}

/** 자주 바뀌는 재생 위치는 별도 흐름으로 분리(목록 화면 잦은 리컴포지션 방지) */
data class Progress(val positionMs: Long = 0L, val durationMs: Long = 0L)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PlaylistStore(app)
    private val playlistMap: LinkedHashMap<String, MutableSet<Long>> = store.load()

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            playlists = playlistMap.keys.toList(),
            playlistMembers = snapshotMembers()
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    var controller: MediaController? = null
        set(value) {
            field = value
            value?.addListener(playerListener)
            if (value != null) {
                syncFromPlayer()
                startPositionLoop()
            }
        }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _uiState.update { it.copy(currentTrackId = mediaItem?.mediaId?.toLongOrNull()) }
            _progress.value = Progress(0L, controller?.duration?.coerceAtLeast(0L) ?: 0L)
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _uiState.update { it.copy(shuffle = enabled) }
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
        }
        override fun onVolumeChanged(volume: Float) {
            _uiState.update { it.copy(volume = volume) }
        }
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        _uiState.update {
            it.copy(
                isPlaying = c.isPlaying,
                currentTrackId = c.currentMediaItem?.mediaId?.toLongOrNull(),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                volume = c.volume
            )
        }
        _progress.value = Progress(c.currentPosition.coerceAtLeast(0L), c.duration.coerceAtLeast(0L))
    }

    private var loopStarted = false
    private fun startPositionLoop() {
        if (loopStarted) return
        loopStarted = true
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    _progress.value = Progress(
                        c.currentPosition.coerceAtLeast(0L),
                        c.duration.coerceAtLeast(0L)
                    )
                }
                delay(400)
            }
        }
    }

    /** 기기에서 음악 목록을 스캔 (권한 획득 후 호출) */
    fun loadTracks() {
        if (_uiState.value.allTracks.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = withContext(Dispatchers.IO) {
                MusicRepository.loadTracks(getApplication())
            }
            val folders = tracks.map { it.folder }.distinct().sorted()
            _uiState.update {
                it.copy(allTracks = tracks, folders = folders, isLoading = false)
            }
        }
    }

    // ---------- 목록 필터링 ----------
    fun tracksForChip(chip: String): List<Track> {
        val state = _uiState.value
        return when (chip) {
            ALL_CHIP -> state.allTracks
            in state.folders -> state.allTracks.filter { it.folder == chip }
            else -> {
                // 사용자 목록은 "저장된 순서"대로 (드래그 정렬 반영)
                val ids = playlistMap[chip] ?: return emptyList()
                val byId = state.allTracks.associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }
        }
    }

    fun shownTracks(): List<Track> {
        val q = _uiState.value.query.trim().lowercase()
        val base = tracksForChip(_uiState.value.activeChip)
        return if (q.isEmpty()) base
        else base.filter { (it.title + " " + it.artist).lowercase().contains(q) }
    }

    fun isCustomPlaylist(chip: String): Boolean = playlistMap.containsKey(chip)

    fun setActiveChip(chip: String) = _uiState.update { it.copy(activeChip = chip) }
    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }

    // ---------- 재생 ----------
    /**
     * 곡을 재생한다. 재생 큐(다음/이전/자동재생 범위)는 검색 결과가 아니라
     * 그 곡이 속한 "현재 칩(전체/폴더/재생목록) 전체"로 만든다.
     * 그래서 검색해서 한 곡을 틀어도, 끝나면 그 목록의 다음 곡으로 이어진다.
     */
    fun playTrack(track: Track) {
        val c = controller ?: return
        val scope = tracksForChip(_uiState.value.activeChip)
        val idx = scope.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        c.setMediaItems(scope.map { it.toMediaItem() }, idx, 0L)
        c.prepare()
        c.play()
    }

    /** 현재 칩 전체를 처음부터 재생 */
    fun playChipFromStart() {
        val c = controller ?: return
        val scope = tracksForChip(_uiState.value.activeChip)
        if (scope.isEmpty()) return
        c.setMediaItems(scope.map { it.toMediaItem() }, 0, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playChipFromStart(); return }
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0L)) }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** off -> all -> one -> off (웹과 동일 순서) */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun cycleSpeed() {
        val next = (_uiState.value.speedIndex + 1) % SPEEDS.size
        _uiState.update { it.copy(speedIndex = next) }
        controller?.setPlaybackSpeed(SPEEDS[next])
    }

    fun setVolume(v: Float) { controller?.volume = v.coerceIn(0f, 1f) }

    // ---------- 재생목록 관리 ----------
    private fun snapshotMembers(): Map<String, Set<Long>> =
        playlistMap.mapValues { it.value.toSet() }

    private fun commitPlaylists() {
        store.save(playlistMap)
        _uiState.update {
            it.copy(
                playlists = playlistMap.keys.toList(),
                playlistMembers = snapshotMembers(),
                playlistRevision = it.playlistRevision + 1
            )
        }
    }

    fun createPlaylist(rawName: String): Boolean {
        val name = rawName.trim()
        if (name.isEmpty() || name == ALL_CHIP || playlistMap.containsKey(name) ||
            name in _uiState.value.folders
        ) return false
        // 새 목록을 맨 앞에 추가 (newest first)
        val merged = LinkedHashMap<String, MutableSet<Long>>()
        merged[name] = LinkedHashSet()
        merged.putAll(playlistMap)
        playlistMap.clear()
        playlistMap.putAll(merged)
        commitPlaylists()
        _uiState.update { it.copy(activeChip = name) }
        return true
    }

    fun addToPlaylist(trackId: Long, name: String) {
        playlistMap.getOrPut(name) { LinkedHashSet() }.add(trackId)
        commitPlaylists()
    }

    /** 여러 곡을 한 �