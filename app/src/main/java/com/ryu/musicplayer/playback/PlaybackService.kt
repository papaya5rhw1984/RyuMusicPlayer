package com.ryu.musicplayer.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 백그라운드 재생 + 알림/잠금화면 컨트롤의 핵심.
 * MediaSessionService가 포그라운드 서비스와 미디어 알림을 자동으로 관리하므로
 * 앱이 백그라운드로 가거나 화면이 꺼져도 재생이 유지된다.
 *
 * v1.3.0:
 *  - 재생 위치를 서비스가 직접 주기적으로 저장 → 앱(Activity)이 폴드/백그라운드로
 *    컨트롤러를 해제해도 '마지막 위치'가 항상 최신으로 남아 이어듣기가 안정적.
 *  - 수면 타이머가 AlarmManager로 ACTION_SLEEP_PAUSE 인텐트를 보내면 여기서 일시정지.
 *  - '곡 끝' 모드(prefs endOfTrack)를 실제 ExoPlayer 전환 콜백에서 직접 감지해 일시정지.
 *    (앱 UI/컨트롤러에 의존하지 않으므로 폴드/포그라운드 모두 안정적으로 멈춘다.)
 */
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_SLEEP_PAUSE = "com.ryu.musicplayer.SLEEP_PAUSE"
        private const val PREFS = "player_state"
        private const val SAVE_INTERVAL_MS = 3000L
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = object : Runnable {
        override fun run() {
            savePosition()
            saveHandler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // 이어폰 뽑으면 일시정지
            .build()
        player = exo

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                savePosition()
                // '곡 끝' 모드: 현재 곡이 자연 종료되어 다음 곡으로 넘어가려는 순간 멈춤
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    if (prefs.getBoolean("endOfTrack", false)) {
                        prefs.edit().putBoolean("endOfTrack", false).apply()
                        exo.pause()
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, exo).build()

        // 재생 중에도 3초마다 위치 저장 (컨트롤러 해제와 무관하게 항상 최신)
        saveHandler.postDelayed(saveRunnable, SAVE_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SLEEP_PAUSE) {
            // 수면 타이머 알람 → 폴드/백그라운드여도 여기서 직접 일시정지
            savePosition()
            player?.pause()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun savePosition() {
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("lastTrack", id)
            .putLong("lastPos", p.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        saveHandler.removeCallbacks(saveRunnable)
        savePosition()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
