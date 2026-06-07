package com.ryu.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ryu.musicplayer.playback.PlaybackService
import com.ryu.musicplayer.ui.PlayerScreen
import com.ryu.musicplayer.ui.PlayerViewModel

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val audioPermission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private fun permissionsToRequest(): Array<String> = buildList {
        add(audioPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS) // 미디어 알림 표시용
    }.toTypedArray()

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGate()
                }
            }
        }
    }

    @Composable
    private fun PermissionGate() {
        var hasPermission by remember { mutableStateOf(hasAudioPermission()) }
        var permanentlyDenied by remember { mutableStateOf(false) }

        // 권한 요청 런처 (Compose 안에서 생성 → 결과가 바로 state에 반영됨)
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            val granted = hasAudioPermission()
            hasPermission = granted
            if (granted) {
                viewModel.loadTracks()
            } else {
                // 시스템 권한창이 더 이상 안 뜨는(다시 묻지 않음) 상태인지 판별
                permanentlyDenied =
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        this@MainActivity, audioPermission
                    )
            }
        }

        // 설정 화면에서 권한을 켜고 돌아왔을 때 자동 반영
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && !hasPermission && hasAudioPermission()) {
                    hasPermission = true
                    permanentlyDenied = false
                    viewModel.loadTracks()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 최초 진입 시 권한이 없으면 한 번 자동 요청
        LaunchedEffect(Unit) {
            if (hasPermission) viewModel.loadTracks()
            else launcher.launch(permissionsToRequest())
        }

        if (hasPermission) {
            PlayerScreen(viewModel = viewModel)
        } else {
            PermissionRequest(
                permanentlyDenied = permanentlyDenied,
                onRequest = { launcher.launch(permissionsToRequest()) },
                onOpenSettings = { openAppSettings() }
            )
        }
    }

    @Composable
    private fun PermissionRequest(
        permanentlyDenied: Boolean,
        onRequest: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (permanentlyDenied)
                    "권한이 거부되어 음악을 불러올 수 없습니다.\n설정에서 '음악 및 오디오' 권한을 허용해 주세요."
                else
                    "기기의 음악을 재생하려면\n음악/오디오 접근 권한이 필요합니다.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            if (permanentlyDenied) {
                Button(onClick = onOpenSettings) { Text("설정 열기") }
            } else {
                Button(onClick = onRequest) { Text("권한 허용") }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // MediaController를 PlaybackService에 연결
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            viewModel.controller = future.get()
            if (hasAudioPermission()) viewModel.loadTracks()
        }, MoreExecutors.directExecutor())
        controllerFuture = future
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        viewModel.controller = null
        super.onStop()
    }
}
