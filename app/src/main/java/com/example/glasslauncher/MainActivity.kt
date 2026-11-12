package com.example.glasslauncher

import android.content.*
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var homeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LauncherApp(homeSignal) }
    }

    // Apăsarea tastei Home închide sertarul / meniurile
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal++
    }
}

private val defaultBackground = Brush.verticalGradient(
    listOf(Color(0xFF0A0E18), Color(0xFF1B1626), Color(0xFF5A3040), Color(0xFFB0643A))
)

@Composable
fun LauncherApp(homeSignal: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var pinnedPrefs by remember { mutableStateOf(prefs.pinned()) }
    var dockPrefs by remember { mutableStateOf(prefs.dock()) }
    var defaultDock by remember { mutableStateOf<List<String>>(emptyList()) }
    var wallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    var drawerOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var homeMenu by remember { mutableStateOf(false) }

    // ---- încărcări ----
    LaunchedEffect(reloadTick) { apps = loadApps(ctx) }
    LaunchedEffect(apps) {
        if (defaultDock.isEmpty() && apps.isNotEmpty()) defaultDock = computeDefaultDock(ctx, apps)
    }
    LaunchedEffect(Unit) { wallpaper = loadWallpaper(ctx) }

    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { reloadTick++ }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }

    LaunchedEffect(homeSignal) { drawerOpen = false; menuApp = null; homeMenu = false }

    // ---- liste derivate ----
    val pinnedPkgs = remember(apps, pinnedPrefs) {
        pinnedPrefs ?: pinnedDefaults.filter { p -> apps.any { it.pkg == p } }
            .ifEmpty { apps.take(8).map { it.pkg } }
    }
    val dockPkgs = dockPrefs ?: defaultDock
    val homeApps = pinnedPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.take(MAX_HOME)
    val dockApps = dockPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.take(MAX_DOCK)

    // ---- acțiuni ----
    fun togglePin(app: AppInfo) {
        val cur = pinnedPkgs
        if (app.pkg in cur) {
            val n = cur - app.pkg; pinnedPrefs = n; prefs.setPinned(n)
        } else if (cur.size >= MAX_HOME) {
            Toast.makeText(ctx, "Home is full ($MAX_HOME apps)", Toast.LENGTH_SHORT).show()
        } else {
            val n = cur + app.pkg; pinnedPrefs = n; prefs.setPinned(n)
        }
    }
    fun toggleDock(app: AppInfo) {
        val cur = dockApps.map { it.pkg }
        val n = if (app.pkg in cur) cur - app.pkg else if (cur.size < MAX_DOCK) cur + app.pkg else cur
        dockPrefs = n; prefs.setDock(n)
    }
    fun safeStart(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            if (saveWallpaper(ctx, uri)) wallpaper = loadWallpaper(ctx)
            else Toast.makeText(ctx, "Could not load image", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = drawerOpen || menuApp != null || homeMenu) {
        when {
            menuApp != null -> menuApp = null
            homeMenu -> homeMenu = false
            else -> drawerOpen = false
        }
    }

    CompositionLocalProvider(LocalWallpaper provides wallpaper, LocalRootSize provides rootSize) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it }
                .pointerInput(drawerOpen) {
                    if (!drawerOpen) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total < -120f) drawerOpen = true },
                            onVerticalDrag = { _, dy -> total += dy }
                        )
                    }
                }
                .pointerInput(drawerOpen) {
                    if (!drawerOpen) detectTapGestures(onLongPress = { homeMenu = true })
                }
        ) {
            // fundal: wallpaper sau gradient cald
            val wp = wallpaper
            if (wp != null) {
                Image(wp.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.12f), Color.Black.copy(0.35f)))))
            } else {
                Box(Modifier.fillMaxSize().background(defaultBackground))
            }

            HomeScreen(
                homeApps = homeApps,
                dockApps = dockApps,
                onAppLongPress = { menuApp = it },
                onSearch = { drawerOpen = true }
            )

            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut()
            ) {
                AppDrawer(apps, onClose = { drawerOpen = false }, onAppLongPress = { menuApp = it })
            }

            menuApp?.let { app ->
                AppMenu(
                    app = app,
                    pinned = app.pkg in pinnedPkgs,
                    inDock = dockApps.any { it.pkg == app.pkg },
                    dockFull = dockApps.size >= MAX_DOCK,
                    onPin = { togglePin(app); menuApp = null },
                    onDock = { toggleDock(app); menuApp = null },
                    onInfo = {
                        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                        menuApp = null
                    },
                    onUninstall = {
                        safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                        menuApp = null
                    },
                    onDismiss = { menuApp = null }
                )
            }

            if (homeMenu) {
                HomeMenu(
                    onPickWallpaper = { homeMenu = false; picker.launch("image/*") },
                    onSystemWallpaper = {
                        homeMenu = false
                        clearCustomWallpaper(ctx)
                        scope.launch { wallpaper = loadWallpaper(ctx) }
                    },
                    onDismiss = { homeMenu = false }
                )
            }
        }
    }
}
