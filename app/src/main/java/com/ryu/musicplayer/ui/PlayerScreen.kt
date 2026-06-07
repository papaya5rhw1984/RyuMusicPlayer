package com.ryu.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ryu.musicplayer.Vinyl
import com.ryu.musicplayer.data.Track
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    // null = 폴더/목록 리스트(브라우즈), 값 있으면 그 컬렉션의 곡 목록(상세)
    var detailChip by remember { mutableStateOf<String?>(null) }

    // 뒤로가기: 지금재생 → 메인, 상세 → 브라우즈, 그 외엔 앱 종료(기본)
    BackHandler(enabled = pagerState.currentPage != 0 || detailChip != null) {
        when {
            pagerState.currentPage != 0 -> scope.launch { pagerState.animateScrollToPage(0) }
            detailChip != null -> detailChip = null
        }
    }

    Column(Modifier.fillMaxSize().background(Vinyl.Bg)) {
        TopTabs(
            selected = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) PlaylistPage(
                state = state,
                viewModel = viewModel,
                detailChip = detailChip,
                onOpen = { chip -> viewModel.setActiveChip(chip); detailChip = chip },
                onBack = { detailChip = null },
                onOpenPlayer = { scope.launch { pagerState.animateScrollToPage(1) } }
            )
            else NowPlayingPage(state, viewModel)
        }
    }
}

@Composable
private fun TopTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        TabItem("재생목록", selected == 0) { onSelect(0) }
        Spacer(Modifier.width(10.dp))
        TabItem("지금 재생", selected == 1) { onSelect(1) }
    }
}

@Composable
private fun TabItem(label: String, on: Boolean, onClick: () -> Unit) {
    val bg = if (on) Brush.linearGradient(listOf(Vinyl.Accent, Vinyl.Accent2))
    else Brush.linearGradient(listOf(Vinyl.Surface, Vinyl.Surface))
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (on) Modifier else Modifier.border(1.dp, Vinyl.Line, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (on) Vinyl.Bg else Vinyl.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/* ---------------- 지금 재생 (LP) ---------------- */

@Composable
private fun NowPlayingPage(state: PlayerUiState, vm: PlayerViewModel) {
    val track = state.currentTrack
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VinylDisc(
            artworkUri = track?.artworkUri?.toString(),
            isPlaying = state.isPlaying,
            onClick = { vm.togglePlayPause() }
        )
        Spacer(Modifier.height(26.dp))
        Text(
            text = track?.title ?: "재생할 음악을 선택하세요",
            color = Vinyl.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = track?.artist ?: "재생목록 탭에서 곡을 골라보세요",
            color = Vinyl.Muted,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(26.dp))
        SeekBar(vm)
        Spacer(Modifier.height(22.dp))
        Controls(state, vm)
        Spacer(Modifier.height(24.dp))
        Extras(state, vm)
    }
}

@Composable
private fun VinylDisc(artworkUri: String?, isPlaying: Boolean, onClick: () -> Unit) {
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 7000,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        }
    }
    Box(
        modifier = Modifier
            .size(280.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // LP판 홈(groove)
        Canvas(Modifier.fillMaxSize().rotate(rotation.value)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF1C1714), radius = r)
            var rr = r
            while (rr > r * 0.32f) {
                drawCircle(
                    color = Color(0xFF15110E),
                    radius = rr,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
                rr -= 6f
            }
        }
        // 가운데 라벨 (앨범아트 또는 음표)
        Box(
            Modifier.size(118.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Vinyl.Accent, Vinyl.Accent2))),
            contentAlignment = Alignment.Center
        ) {
            // 폴백 음표(아래) 위에 앨범아트를 덮어 그림
            Icon(
                Icons.Filled.MusicNote, contentDescription = null,
                tint = Vinyl.Bg.copy(alpha = 0.7f), modifier = Modifier.size(40.dp)
            )
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
        }
        // 중심 구멍
        Box(Modifier.size(18.dp).clip(CircleShape).background(Vinyl.Bg))
    }
}

@Composable
private fun SeekBar(vm: PlayerViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val dur = progress.durationMs.coerceAtLeast(1L)
    var dragFrac by remember { mutableStateOf<Float?>(null) }      // 끄는 중일 때만 값
    var pendingTarget by remember { mutableStateOf<Long?>(null) }  // 손 뗀 뒤 따라잡을 때까지 유지

    // 실제 재생위치가 목표에 근접하면 pending 해제 (튐 방지)
    LaunchedEffect(progress.positionMs, pendingTarget) {
        val tgt = pendingTarget
        if (tgt != null && kotlin.math.abs(progress.positionMs - tgt) < 700) pendingTarget = null
    }

    val displayMs: Long = when {
        dragFrac != null -> (dragFrac!! * dur).toLong()
        pendingTarget != null -> pendingTarget!!
        else -> progress.positionMs
    }
    val frac = (displayMs.toFloat() / dur).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = frac,
            onValueChange = { dragFrac = it },
            onValueChangeFinished = {
                val target = ((dragFrac ?: frac) * dur).toLong()
                vm.seekTo(target)
                pendingTarget = target
                dragFrac = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Vinyl.Text,
                activeTrackColor = Vinyl.Accent,
                inactiveTrackColor = Vinyl.SurfaceHi
            )
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(fmt(displayMs), color = Vinyl.Faint, fontSize = 12.sp)
            Text(fmt(progress.durationMs), color = Vinyl.Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Controls(state: PlayerUiState, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vm.toggleShuffle() }) {
            Icon(Icons.Filled.Shuffle, "셔플",
                tint = if (state.shuffle) Vinyl.Accent else Vinyl.Muted)
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = { vm.previous() }) {
            Icon(Icons.Filled.SkipPrevious, "이전", tint = Vinyl.Text, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Vinyl.Accent, Vinyl.Accent2)))
                .clickable { vm.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (state.isPlaying) "일시정지" else "재생",
                tint = Vinyl.Bg, modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = { vm.next() }) {
            Icon(Icons.Filled.SkipNext, "다음", tint = Vinyl.Text, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = { vm.cycleRepeat() }) {
            val icon = if (state.repeatMode == Player.REPEAT_MODE_ONE)
                Icons.Filled.RepeatOne else Icons.Filled.Repeat
            Icon(icon, "반복",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) Vinyl.Accent else Vinyl.Muted)
        }
    }
}

@Composable
private fun Extras(state: PlayerUiState, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (state.volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            "음량", tint = Vinyl.Faint, modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Slider(
            value = state.volume,
            onValueChange = { vm.setVolume(it) },
            modifier = Modifier.width(120.dp),
            colors = SliderDefaults.colors(
                thumbColor = Vinyl.Muted,
                activeTrackColor = Vinyl.Muted,
                inactiveTrackColor = Vinyl.SurfaceHi
            )
        )
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, Vinyl.Line, RoundedCornerShape(9.dp))
                .background(Vinyl.Surface).clickable { vm.cycleSpeed() }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(speedLabel(state.speed), color = Vinyl.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ---------------- 재생목록 ---------------- */

@Composable
private fun PlaylistPage(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    detailChip: String?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (detailChip == null) {
            BrowseList(
                state = state,
                viewModel = viewModel,
                onOpen = onOpen,
                onCreate = { showCreate = true },
                onLicense = { showLicense = true },
                modifier = Modifier.weight(1f)
            )
        } else {
            DetailView(
                state = state,
                viewModel = viewModel,
                chip = detailChip,
                onBack = onBack,
                onOpenPlayer = onOpenPlayer,
                onCreate = { showCreate = true },
                modifier = Modifier.weight(1f)
            )
        }

        // 하단 미니 플레이어 (브라우즈/상세 공통)
        state.currentTrack?.let { cur ->
            MiniPlayer(
                track = cur,
                isPlaying = state.isPlaying,
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onOpen = onOpenPlayer
            )
        }
    }

    if (showCreate) {
        NameDialog(
            title = "새 재생목록",
            onConfirm = { name -> if (viewModel.createPlaylist(name)) onOpen(name.trim()); showCreate = false },
            onDismiss = { showCreate = false }
        )
    }
    if (showLicense) {
        LicenseDialog(onDismiss = { showLicense = false })
    }
}

/* 브라우즈: 전체 + 내 목록 + 폴더 리스트 (탭하면 상세로) */
@Composable
private fun BrowseList(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onLicense: () -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fk = from.key as? String
        val tk = to.key as? String
        if (fk != null && tk != null && fk.startsWith("pl_") && tk.startsWith("pl_")) {
            viewModel.movePlaylistByKey(fk, tk)
        }
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(11.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Vinyl.Accent, Vinyl.Accent2))))
            Spacer(Modifier.width(10.dp))
            Text("Ryu Music", color = Vinyl.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onLicense) {
                Icon(Icons.Filled.Info, "오픈소스 라이선스", tint = Vinyl.Faint, modifier = Modifier.size(18.dp))
            }
        }
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
            item {
                CollectionRow(ALL_CHIP, state.allTracks.size, Icons.Filled.LibraryMusic, onClick = { onOpen(ALL_CHIP) })
            }
            item { SectionHeader("내 목록 (길게 눌러 이동)", actionLabel = "+ 새 목록", onAction = onCreate) }
            if (state.playlists.isEmpty()) {
                item {
                    Text(
                        "아직 만든 목록이 없어요. '+ 새 목록'으로 만들어보세요.",
                        color = Vinyl.Faint, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
            items(state.playlists, key = { "pl_$it" }) { name ->
                ReorderableItem(reorderState, key = "pl_$name") { isDragging ->
                    Surface(color = if (isDragging) Vinyl.SurfaceHi else Color.Transparent) {
                        CollectionRow(
                            name = name,
                            count = state.playlistMembers[name]?.size ?: 0,
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            onClick = { onOpen(name) },
                            onRename = { renameTarget = name },
                            onDelete = { deleteTarget = name },
                            dragModifier = Modifier.longPressDraggableHandle()
                        )
                    }
                }
            }
            item { SectionHeader("폴더", actionLabel = null, onAction = {}) }
            items(state.folders, key = { "fd_$it" }) { f ->
                CollectionRow(f, state.allTracks.count { it.folder == f }, Icons.Filled.Folder, onClick = { onOpen(f) })
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    renameTarget?.let { old ->
        NameDialog(
            title = "이름 변경",
            initial = old,
            confirmLabel = "변경",
            onConfirm = { newName -> viewModel.renamePlaylist(old, newName); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Vinyl.Surface,
            title = { Text("목록 삭제", color = Vinyl.Text) },
            text = { Text("'$name' 목록을 삭제할까요?\n(기기의 음악 파일은 삭제되지 않아요)", color = Vinyl.Muted) },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePlaylist(name); deleteTarget = null }) {
                    Text("삭제", color = Vinyl.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소", color = Vinyl.Muted) }
            }
        )
    }
}

@Composable
private fun CollectionRow(
    name: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    dragModifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().then(dragModifier).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Vinyl.Surface),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Vinyl.Accent, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(name, color = Vinyl.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${count}곡", color = Vinyl.Faint, fontSize = 12.sp)
        if (onRename != null || onDelete != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, "더보기", tint = Vinyl.Faint, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onRename != null) DropdownMenuItem(
                        text = { Text("이름 변경") },
                        onClick = { menuOpen = false; onRename() }
                    )
                    if (onDelete != null) DropdownMenuItem(
                        text = { Text("삭제", color = Vinyl.Danger) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        } else {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = Vinyl.Faint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Vinyl.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (actionLabel != null) {
            Box(
                Modifier.clip(CircleShape).border(1.dp, Vinyl.Accent, CircleShape)
                    .clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text(actionLabel, color = Vinyl.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/* 상세: 선택한 컬렉션의 곡 목록 */
@Composable
private fun DetailView(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    chip: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = remember(state, chip) { viewModel.shownTracks() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isCustom = viewModel.isCustomPlaylist(chip)
    var showAddAll by remember { mutableStateOf(false) }
    var showSongPicker by remember { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fk = from.key as? Long
        val tk = to.key as? Long
        if (fk != null && tk != null) viewModel.moveTrackInPlaylist(chip, fk, tk)
    }
    val canReorder = isCustom && state.query.isBlank()

    Column(modifier) {
        // 헤더: 뒤로 + 이름 + 현재곡 이동 + 추가
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButt