package com.ryu.musicplayer.ui

import android.app.Application
import android.content.Context
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
    val folders: List<String> = emptyList(),
    val playlists: List<String> = emptyList(),
    val playlistMembers: Map<String, Set<Long>> = emptyMap(),
    val playlistRevision: Int = 0,
    val activeChip: String = ALL_CHIP,
    val query: String = "",
    val isLoading: Boolean = false,
    val currentTrackId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speedIndex: Int = 0,
    val volume: Float = 1f,
    val sleepRemainMin: Int? = null,   // v1.0.3: 수면 타이머 남은 분(null=꺼짐)
    val sleepEndOfTrack: Boolean = false   // v1.2.0: '이 곡 끝까지' 모드
) {
    val currentTrack: Track? get() = allTracks.firstOrNull { it.id == currentTrackId }
    val speed: Float get() = SPEEDS[speedIndex]
}

/** 자주 바뀌는 재생 위치는 별도 흐름으로 분리(목록 화면 잦은 리컴포지션 방지) */
data class Progress(val positionMs: Long = 0L, val durationMs: Long = 0L)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PlaylistStore(app)
    private val statePrefs = app.getSharedPreferences("player_state", Context.MODE_PRIVATE)
    private var restored = false
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
                value.shuffleModeEnabled = statePrefs.getBoolean("shuffle", false)
                value.repeatMode = statePrefs.getInt("repeat", Player.REPEAT_MODE_OFF)
                // v1.0.3: 재생속도 복원 (기존엔 재시작 시 1x로 초기화)
                val si = statePrefs.getInt("speedIdx", 0).coerceIn(0, SPEEDS.size - 1)
                _uiState.update { it.copy(speedIndex = si) }
                value.setPlaybackSpeed(SPEEDS[si])
                syncFromPlayer()
                startPositionLoop()
                maybeRestoreLast()
            }
        }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull()
            _uiState.update { it.copy(currentTrackId = id) }
            // v1.2.0: '이 곡 끝까지' 모드 — 곡이 자연 종료되어 다음 곡으로 넘어가는 순간 일시정지
            if (_uiState.value.sleepEndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                controller?.pause()
                _uiState.update { it.copy(sleepEndOfTrack = false) }
            }
            // v1.1.0: 곡이 '실제로 바뀐' 경우에만 위치 리셋 — 복원 직후 전환 이벤트가 lastPos를 지우던 결함 방지
            if (id != null) {
                val prev = statePrefs.getLong("lastTrack", -1L)
                val e = statePrefs.edit().putLong("lastTrack", id)
                if (prev != id) e.putLong("lastPos", 0L)
                e.apply()
            }
            _progress.value = Progress(0L, controller?.duration?.coerceAtLeast(0L) ?: 0L)
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _uiState.update { it.copy(shuffle = enabled) }
            statePrefs.edit().putBoolean("shuffle", enabled).apply()
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
            statePrefs.edit().putInt("repeat", repeatMode).apply()
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
    private var posSaveTick = 0
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
                    // v1.0.3: 재생 중 4초마다 위치 저장 → 다음 실행 때 이어듣기
                    if (c.isPlaying) {
                        posSaveTick++
                        if (posSaveTick % 10 == 0) statePrefs.edit().putLong("lastPos", c.currentPosition.coerceAtLeast(0L)).apply()
                    }
                    // v1.0.3: 수면 타이머 — 시간이 다 되면 일시정지
                    val end = sleepEndAt
                    if (end != null) {
                        if (System.currentTimeMillis() >= end) {
                            sleepEndAt = null
                            c.pause()
                        }
                        updateSleepRemain()
                    }
                }
                delay(400)
            }
        }
    }

    // ---------- 수면 타이머 (끄기 → 15분 → 30분 → 60분 → 곡 끝 → 끄기) ----------
    private var sleepEndAt: Long? = null
    fun cycleSleepTimer() {
        val now = System.currentTimeMillis()
        // v1.2.0: '곡 끝' 모드에서 한 번 더 누르면 끄기
        if (_uiState.value.sleepEndOfTrack) {
            _uiState.update { it.copy(sleepEndOfTrack = false) }
            sleepEndAt = null; updateSleepRemain(); return
        }
        val end = sleepEndAt
        sleepEndAt = if (end == null) now + 15 * 60_000L else {
            val remainMin = (end - now) / 60_000L
            when {
                remainMin >= 31 -> { _uiState.update { it.copy(sleepEndOfTrack = true) }; null }  // 60분대 → 곡 끝
                remainMin >= 16 -> now + 60 * 60_000L    // 30분대 → 60분
                else -> now + 30 * 60_000L               // 15분대 → 30분
            }
        }
        updateSleepRemain()
    }

    /** v1.2.0: ±10초 탐색 */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val dur = c.duration
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        c.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
    }
    private fun updateSleepRemain() {
        val end = sleepEndAt
        val remain = if (end == null) null
        else (((end - System.currentTimeMillis()) + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
        _uiState.update { it.copy(sleepRemainMin = remain) }   // 동일 값이면 StateFlow가 재방출 안 함
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
            maybeRestoreLast()
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
    fun playTrack(track: Track) {
        val c = controller ?: return
        val scope = tracksForChip(_uiState.value.activeChip)
        val idx = scope.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        c.setMediaItems(scope.map { it.toMediaItem() }, idx, 0L)
        c.prepare()
        c.play()
        statePrefs.edit().putString("lastChip", _uiState.value.activeChip).putLong("lastTrack", track.id).apply()
    }

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
        statePrefs.edit().putInt("speedIdx", next).apply()   // v1.0.3: 배속 저장
    }

    fun setVolume(v: Float) { controller?.volume = v.coerceIn(0f, 1f) }

    /** v1.1.0: 화면을 떠나는 순간(onStop) 현재 곡·위치를 스냅샷 저장.
     *  기존엔 onStop에서 컨트롤러를 해제해 백그라운드 동안 '마지막 곡' 기록이 멈췄고,
     *  그 상태에서 프로세스가 죽으면 복원이 어긋났다. */
    fun saveNowState() {
        val c = controller ?: return
        val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        statePrefs.edit()
            .putLong("lastTrack", id)
            .putLong("lastPos", c.currentPosition.coerceAtLeast(0L))
            .putString("lastChip", _uiState.value.activeChip)
            .apply()
    }

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

    /** 여러 곡을 한 번에 목록에 추가 (곡 고르기 창에서 사용) */
    fun addManyToPlaylist(ids: Collection<Long>, name: String) {
        if (ids.isEmpty()) return
        playlistMap.getOrPut(name) { LinkedHashSet() }.addAll(ids)
        commitPlaylists()
    }

    /** 곡 고르기 창용: 전체 라이브러리를 검색어/폴더로 필터 */
    fun libraryFor(query: String, folder: String?): List<Track> {
        val q = query.trim().lowercase()
        return _uiState.value.allTracks.filter { t ->
            (folder == null || t.folder == folder) &&
                (q.isEmpty() || (t.title + " " + t.artist).lowercase().contains(q))
        }
    }

    fun addAllShownToPlaylist(name: String) {
        val set = playlistMap.getOrPut(name) { LinkedHashSet() }
        shownTracks().forEach { set.add(it.id) }
        commitPlaylists()
    }

    fun removeFromPlaylist(trackId: Long, name: String) {
        playlistMap[name]?.remove(trackId)
        commitPlaylists()
    }

    /** 현재 보고 있는 사용자 재생목록 비우기 */
    fun clearActivePlaylist() {
        val name = _uiState.value.activeChip
        if (playlistMap.containsKey(name)) {
            playlistMap[name]?.clear()
            commitPlaylists()
        }
    }

    /** 내 목록 순서 이동 (드래그) — 키는 "pl_<이름>" */
    fun movePlaylistByKey(fromKey: String, toKey: String) {
        val from = fromKey.removePrefix("pl_")
        val to = toKey.removePrefix("pl_")
        if (from == to) return
        val names = playlistMap.keys.toMutableList()
        val fi = names.indexOf(from)
        val ti = names.indexOf(to)
        if (fi < 0 || ti < 0) return
        names.add(ti, names.removeAt(fi))
        val rebuilt = LinkedHashMap<String, MutableSet<Long>>()
        names.forEach { rebuilt[it] = playlistMap[it] ?: LinkedHashSet() }
        playlistMap.clear()
        playlistMap.putAll(rebuilt)
        commitPlaylists()
    }

    /** 목록 안 곡 순서 이동 (드래그) */
    fun moveTrackInPlaylist(playlist: String, fromId: Long, toId: Long) {
        if (fromId == toId) return
        val set = playlistMap[playlist] ?: return
        val ids = set.toMutableList()
        val fi = ids.indexOf(fromId)
        val ti = ids.indexOf(toId)
        if (fi < 0 || ti < 0) return
        ids.add(ti, ids.removeAt(fi))
        set.clear()
        set.addAll(ids)
        commitPlaylists()
    }

    /** 사용자 목록 이름 변경 (순서·내용 유지) */
    fun renamePlaylist(old: String, newRaw: String): Boolean {
        val name = newRaw.trim()
        if (name.isEmpty() || name == ALL_CHIP || name in _uiState.value.folders) return false
        if (!playlistMap.containsKey(old)) return false
        if (name == old) return true
        if (playlistMap.containsKey(name)) return false
        val rebuilt = LinkedHashMap<String, MutableSet<Long>>()
        playlistMap.forEach { (k, v) -> rebuilt[if (k == old) name else k] = v }
        playlistMap.clear()
        playlistMap.putAll(rebuilt)
        commitPlaylists()
        if (_uiState.value.activeChip == old) _uiState.update { it.copy(activeChip = name) }
        return true
    }

    fun deletePlaylist(name: String) {
        if (playlistMap.remove(name) != null) {
            commitPlaylists()
            if (_uiState.value.activeChip == name) _uiState.update { it.copy(activeChip = ALL_CHIP) }
        }
    }

    /** 앱 재시작 시 마지막에 선택했던 곡을 일시정지 상태로 복원(자동재생 안 함) */
    private fun maybeRestoreLast() {
        val c = controller ?: return
        if (restored) return
        if (c.mediaItemCount > 0) { restored = true; return }   // 이미 재생 중이면 건드리지 않음
        val tracks = _uiState.value.allTracks
        if (tracks.isEmpty()) return                            // 트랙 로드 후 다시 시도됨
        val lastId = statePrefs.getLong("lastTrack", -1L)
        if (lastId < 0L) { restored = true; return }
        val savedChip = statePrefs.getString("lastChip", ALL_CHIP) ?: ALL_CHIP
        var chip = if (savedChip == ALL_CHIP || playlistMap.containsKey(savedChip) || savedChip in _uiState.value.folders) savedChip else ALL_CHIP
        var scope = tracksForChip(chip)
        var idx = scope.indexOfFirst { it.id == lastId }
        // v1.1.0: 듣던 목록에서 곡이 빠졌거나 목록이 삭제된 경우 '전체'에서 다시 찾아 복원 (기존엔 그냥 포기 → 음악 유지 실패의 주원인 중 하나)
        if (idx < 0 && chip != ALL_CHIP) {
            chip = ALL_CHIP; scope = tracksForChip(ALL_CHIP)
            idx = scope.indexOfFirst { it.id == lastId }
        }
        if (idx < 0) { restored = true; return }
        restored = true
        _uiState.update { it.copy(activeChip = chip, currentTrackId = lastId) }
        c.playWhenReady = false
        // v1.0.3: 마지막 재생 위치까지 복원(이어듣기) — lastPos는 항상 lastTrack의 위치
        val lastPos = statePrefs.getLong("lastPos", 0L).coerceAtLeast(0L)
        c.setMediaItems(scope.map { it.toMediaItem() }, idx, lastPos)
        c.prepare()
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        super.onCleared()
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()
}
