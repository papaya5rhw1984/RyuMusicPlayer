package com.ryu.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    var detailChip by remember { mutableStateOf<String?>(null) }

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
        Box(
            Modifier.size(118.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Vinyl.Accent, Vinyl.Accent2))),
            contentAlignment = Alignment.Center
        ) {
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
        Box(Modifier.size(18.dp).clip(CircleShape).background(Vinyl.Bg))
    }
}

@Composable
private fun SeekBar(vm: PlayerViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val dur = progress.durationMs.coerceAtLeast(1L)
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var pendingTarget by remember { mutableStateOf<Long?>(null) }

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

/* ---------------- 재생목록 (드릴다운) ---------------- */

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

@OptIn(ExperimentalFoundationApi::class)
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

@OptIn(ExperimentalFoundationApi::class)
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
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Vinyl.Text)
            }
            Text(chip, color = Vinyl.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val idx = shown.indexOfFirst { it.id == state.currentTrackId }
                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
            }) { Icon(Icons.Filled.GpsFixed, "현재 곡으로", tint = Vinyl.Muted, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = {
                if (isCustom) showSongPicker = true else if (shown.isNotEmpty()) showAddAll = true
            }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "곡 추가", tint = Vinyl.Muted) }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.setQuery(it) },
            placeholder = { Text("제목·아티스트 검색", color = Vinyl.Faint) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Vinyl.Faint) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Vinyl.Accent,
                unfocusedBorderColor = Vinyl.Line,
                focusedTextColor = Vinyl.Text,
                unfocusedTextColor = Vinyl.Text,
                cursorColor = Vinyl.Accent,
                focusedContainerColor = Vinyl.Surface,
                unfocusedContainerColor = Vinyl.Surface
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${shown.size}곡", color = Vinyl.Faint, fontSize = 12.sp)
            if (isCustom) {
                Row(
                    Modifier.clickable { viewModel.clearActivePlaylist() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = Vinyl.Faint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("목록 비우기", color = Vinyl.Faint, fontSize = 12.sp)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (state.query.isNotBlank()) "검색 결과가 없어요."
                        else if (isCustom) "이 목록이 비어있어요."
                        else "표시할 음악이 없어요.",
                        color = Vinyl.Faint, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (isCustom && state.query.isBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showSongPicker = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Vinyl.Accent, contentColor = Vinyl.Bg
                            )
                        ) { Text("+ 곡 추가") }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                    items(shown, key = { it.id }) { track ->
                        if (canReorder) {
                            ReorderableItem(reorderState, key = track.id) { isDragging ->
                                Surface(color = if (isDragging) Vinyl.SurfaceHi else Color.Transparent) {
                                    TrackRow(
                                        track = track,
                                        isCurrent = track.id == state.currentTrackId,
                                        isPlaying = state.isPlaying,
                                        playlists = state.playlists,
                                        inCustomPlaylist = isCustom,
                                        onClick = { viewModel.playTrack(track); onOpenPlayer() },
                                        onAddTo = { name -> viewModel.addToPlaylist(track.id, name) },
                                        onCreateAndAdd = onCreate,
                                        onRemove = { viewModel.removeFromPlaylist(track.id, chip) },
                                        dragModifier = Modifier.longPressDraggableHandle()
                                    )
                                }
                            }
                        } else {
                            TrackRow(
                                track = track,
                                isCurrent = track.id == state.currentTrackId,
                                isPlaying = state.isPlaying,
                                playlists = state.playlists,
                                inCustomPlaylist = isCustom,
                                onClick = { viewModel.playTrack(track); onOpenPlayer() },
                                onAddTo = { name -> viewModel.addToPlaylist(track.id, name) },
                                onCreateAndAdd = onCreate,
                                onRemove = { viewModel.removeFromPlaylist(track.id, chip) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    if (showAddAll) {
        PickPlaylistDialog(
            playlists = state.playlists,
            onPick = { viewModel.addAllShownToPlaylist(it); showAddAll = false },
            onCreate = { name -> if (viewModel.createPlaylist(name)) viewModel.addAllShownToPlaylist(name); showAddAll = false },
            onDismiss = { showAddAll = false }
        )
    }
    if (showSongPicker) {
        SongPickerDialog(
            viewModel = viewModel,
            folders = state.folders,
            targetName = chip,
            onConfirm = { ids -> viewModel.addManyToPlaylist(ids, chip); showSongPicker = false },
            onDismiss = { showSongPicker = false }
        )
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Vinyl.SurfaceHi)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Vinyl.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Vinyl.Faint, modifier = Modifier.size(18.dp))
            if (track.artworkUri != null) {
                AsyncImage(track.artworkUri.toString(), null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Vinyl.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Vinyl.Muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (isPlaying) "일시정지" else "재생", tint = Vinyl.Accent, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, "다음", tint = Vinyl.Text, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    val libs = listOf(
        "Jetpack Compose / AndroidX" to "Apache License 2.0",
        "AndroidX Media3 (ExoPlayer)" to "Apache License 2.0",
        "Coil" to "Apache License 2.0",
        "Google Guava" to "Apache License 2.0",
        "Material Icons" to "Apache License 2.0",
        "Kotlin / Coroutines" to "Apache License 2.0",
        "Reorderable (sh.calvin)" to "Apache License 2.0"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vinyl.Surface,
        title = { Text("오픈소스 라이선스", color = Vinyl.Text, fontSize = 16.sp) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "이 앱은 아래 오픈소스 라이브러리를 사용합니다.",
                    color = Vinyl.Muted, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                libs.forEach { (name, lic) ->
                    Text(name, color = Vinyl.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(lic, color = Vinyl.Faint, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Licensed under the Apache License, Version 2.0. " +
                        "You may obtain a copy of the License at " +
                        "http://www.apache.org/licenses/LICENSE-2.0. " +
                        "Unless required by applicable law or agreed to in writing, software " +
                        "distributed under the License is distributed on an \"AS IS\" BASIS, " +
                        "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.",
                    color = Vinyl.Faint, fontSize = 9.sp, lineHeight = 13.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", color = Vinyl.Accent) } }
    )
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    playlists: List<String>,
    inCustomPlaylist: Boolean,
    onClick: () -> Unit,
    onAddTo: (String) -> Unit,
    onCreateAndAdd: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (isCurrent) Vinyl.SurfaceHi else Color.Transparent)
            .then(dragModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(Vinyl.SurfaceHi),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Vinyl.Faint, modifier = Modifier.size(20.dp))
            if (track.artworkUri != null) {
                AsyncImage(track.artworkUri.toString(), null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (isCurrent) Vinyl.Accent else Vinyl.Text,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Vinyl.Muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent && isPlaying) {
            Icon(Icons.Filled.GraphicEq, null, tint = Vinyl.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "목록에 추가", tint = Vinyl.Faint, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                playlists.forEach { pl ->
                    DropdownMenuItem(text = { Text(pl) }, onClick = { onAddTo(pl); menuOpen = false })
                }
                DropdownMenuItem(text = { Text("+ 새 목록…") }, onClick = { onCreateAndAdd(); menuOpen = false })
            }
        }
        if (inCustomPlaylist) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, "목록에서 제거", tint = Vinyl.Faint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/* ---------------- 다이얼로그 ---------------- */

@Composable
private fun SongPickerDialog(
    viewModel: PlayerViewModel,
    folders: List<String>,
    targetName: String,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<Long>() }
    val library = viewModel.libraryFor(query, folder)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Vinyl.Bg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("\"$targetName\"에 곡 추가", color = Vinyl.Text,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("제목·아티스트 검색", color = Vinyl.Faint) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Vinyl.Faint) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Vinyl.Accent, unfocusedBorderColor = Vinyl.Line,
                        focusedTextColor = Vinyl.Text, unfocusedTextColor = Vinyl.Text,
                        cursorColor = Vinyl.Accent,
                        focusedContainerColor = Vinyl.Surface, unfocusedContainerColor = Vinyl.Surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item { PickChip("전체", folder == null) { folder = null } }
                    items(folders, key = { it }) { f ->
                        PickChip(f, folder == f) { folder = f }
                    }
                }
                Button(
                    onClick = { onConfirm(library.map { it.id }) },
                    enabled = library.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vinyl.Accent, contentColor = Vinyl.Bg,
                        disabledContainerColor = Vinyl.Surface, disabledContentColor = Vinyl.Faint
                    )
                ) {
                    Icon(Icons.Filled.LibraryAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (folder == null) "전체 ${library.size}곡 모두 추가"
                        else "'$folder' 폴더 ${library.size}곡 모두 추가",
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("또는 개별 선택 · ${selected.size}곡", color = Vinyl.Muted, fontSize = 12.sp)
                    TextButton(onClick = {
                        val ids = library.map { it.id }
                        if (ids.all { selected.contains(it) }) selected.removeAll(ids)
                        else ids.forEach { if (!selected.contains(it)) selected.add(it) }
                    }) { Text("보이는 곡 전체선택", color = Vinyl.Accent, fontSize = 12.sp) }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(library, key = { it.id }) { t ->
                        val checked = selected.contains(t.id)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (checked) selected.remove(t.id) else selected.add(t.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { if (checked) selected.remove(t.id) else selected.add(t.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Vinyl.Accent, uncheckedColor = Vinyl.Faint,
                                    checkmarkColor = Vinyl.Bg
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = Vinyl.Text, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, color = Vinyl.Muted, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("취소", color = Vinyl.Muted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selected.toList()) },
                        enabled = selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Vinyl.Accent, contentColor = Vinyl.Bg,
                            disabledContainerColor = Vinyl.Surface, disabledContentColor = Vinyl.Faint
                        )
                    ) { Text("추가 (${selected.size})") }
                }
            }
        }
    }
}

@Composable
private fun PickChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .then(if (on) Modifier.background(Brush.linearGradient(listOf(Vinyl.Accent, Vinyl.Accent2)))
            else Modifier.background(Vinyl.Surface).border(1.dp, Vinyl.Line, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (on) Vinyl.Bg else Vinyl.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "만들기",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vinyl.Surface,
        title = { Text(title, color = Vinyl.Text) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text("목록 이름", color = Vinyl.Faint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Vinyl.Accent, unfocusedBorderColor = Vinyl.Line,
                    focusedTextColor = Vinyl.Text, unfocusedTextColor = Vinyl.Text,
                    cursorColor = Vinyl.Accent
                )
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel, color = Vinyl.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = Vinyl.Muted) } }
    )
}

@Composable
private fun PickPlaylistDialog(
    playlists: List<String>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("새 목록에 전체 추가", onConfirm = onCreate, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vinyl.Surface,
        title = { Text("어느 목록에 추가할까요?", color = Vinyl.Text) },
        text = {
            Column {
                if (playlists.isEmpty()) Text("아직 목록이 없어요. 새로 만들 수 있어요.", color = Vinyl.Muted)
                playlists.forEach { pl ->
                    Text(pl, color = Vinyl.Text,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(pl) }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("+ 새 목록", color = Vinyl.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = Vinyl.Muted) } }
    )
}

/* ---------------- 유틸 ---------------- */

private fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun speedLabel(s: Float): String =
    if (s == s.toLong().toFloat()) "${s.toLong()}x" else "${s}x"
