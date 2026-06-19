package com.ryu.musicplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * v1.3.0: 수면 타이머 알람(AlarmManager) 수신기.
 * 이미 포그라운드로 돌고 있는 PlaybackService에 ACTION_SLEEP_PAUSE를 전달해
 * 폴드/화면꺼짐/도즈 상태에서도 재생을 멈춘다. (앱 UI 루프에 의존하지 않음)
 */
class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_SLEEP_PAUSE)
        try {
            context.startService(svc)
        } catch (_: Throwable) {
            // 서비스가 이미 종료된 경우(재생 중이 아니면 멈출 것도 없음) 무시
        }
    }
}
