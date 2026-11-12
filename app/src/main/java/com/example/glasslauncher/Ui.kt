package com.example.glasslauncher
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.content.*
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalWallpaper = compositionLocalOf<Wallpaper?> { null }
val LocalRootSize = compositionLocalOf { IntSize.Zero }

private val White70 = Color.White.copy(alpha = 0.7f)

// ---------- Glass cu blur real al wallpaper-ului ----------
/**
 * Container "frosted glass": desenează versiunea blurată a wallpaper-ului exact în
 * poziția sa de pe ecran (efect de backdrop blur), plus tentă albă și bordură de 1dp.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape,
    fill: Color = Color.White.copy(alpha = 0.14f),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val wp = LocalWallpaper.current
    val root = LocalRootSize.current
    var pos by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { pos = it.positionInRoot().round() },
        contentAlignment = contentAlignment
    ) {
        if (wp != null && root.width > 0 && root.height > 0) {
            Image(
                bitmap = wp.blurred,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.layout { measurable, _ ->
                    val p = measurable.measure(Constraints.fixed(root.width, root.height))
                    layout(0, 0) { p.place(-pos.x, -pos.y) }
                }
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(fill)
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
        )
        content()
    }
}

// ---------- Home ----------
@Composable
fun HomeScreen(
    homeApps: List<AppInfo>,
    dockApps: List<AppInfo>,
    onAppLongPress: (AppInfo) -> Unit,
    onSearch: () -> Unit
) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        fun readBattery(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0) battery = level * 100 / scale
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                now = Date()
                if (i.action == Intent.ACTION_BATTERY_CHANGED) readBattery(i)
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        readBattery(ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_EXPORTED))
        onDispose { ctx.unregisterReceiver(r) }
    }

    fun open(i: Intent) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Zona 1: dashboard
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClockCard(now, Modifier.weight(2f).clickable { open(Intent(AlarmClock.ACTION_SHOW_ALARMS)) })
            DateCard(now, Modifier.weight(2f).clickable {
                open(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")))
            })
            MiniClockBattery(now, battery, Modifier.weight(1f).clickable { open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) })
        }

        Spacer(Modifier.height(20.dp))

        // Zona 2 + 3: grila de aplicații + capsula verticală
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(4f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                homeApps.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                AppIcon(app, true, onAppLongPress)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                if (dockApps.isNotEmpty()) {
                    GlassBox(shape = RoundedCornerShape(36.dp)) {
                        Column(
                            Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) { dockApps.forEach { AppIcon(it, false, onAppLongPress) } }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Zona 4: indicator pagini + buton căutare
        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
            PageIndicator()
            GlassBox(
                modifier = Modifier.align(Alignment.CenterEnd).size(44.dp).clickable { onSearch() },
                shape = CircleShape,
                contentAlignment = Alignment.Center
            ) { SearchGlyph() }
        }
    }
}

// ---------- Widgets ----------
@Composable
fun ClockCard(now: Date, modifier: Modifier) {
    GlassBox(modifier.aspectRatio(1f), RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            ClockGlyph(Modifier.align(Alignment.TopStart).size(14.dp))
            Text(fmt("HH:mm", now), 32.sp, Color.White, Modifier.align(Alignment.CenterStart))
            Text("Local time", 11.sp, White70, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
fun DateCard(now: Date, modifier: Modifier) {
    GlassBox(modifier.aspectRatio(1f), RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Text(fmt("EEEE", now), 12.sp, Color.White.copy(0.8f), Modifier.align(Alignment.TopStart))
            Text(fmt("d", now), 36.sp, Color.White, Modifier.align(Alignment.CenterStart))
            Text(fmt("MMMM", now), 12.sp, White70, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
fun MiniClockBattery(now: Date, battery: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmt("HH:mm", now), 16.sp, Color.White, weight = FontWeight.Bold)
        Text(fmt("MMM d", now), 10.sp, White70)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.size(54.dp)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(0.25f), 0f, 360f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White, -90f, 360f * battery / 100f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            val c = Offset(size.width / 2, size.height / 2)
            val angle = Math.toRadians(-90.0 + 360.0 * battery / 100.0)
            val end = Offset(
                c.x + (size.width * 0.22f * Math.cos(angle)).toFloat(),
                c.y + (size.height * 0.22f * Math.sin(angle)).toFloat()
            )
            drawLine(Color.White, c, end, 2.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White, 2.5.dp.toPx(), c)
        }
        Spacer(Modifier.height(4.dp))
        Text("$battery%", 10.sp, Color.White)
    }
}

// ---------- Iconițe ----------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(app: AppInfo, showLabel: Boolean, onLongPress: (AppInfo) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { try { ctx.startActivity(app.intent) } catch (_: Exception) {} },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(app)
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon, contentDescription = app.label,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
        )
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(app.label, 11.sp, Color.White, Modifier.widthIn(max = 70.dp),
                shadow = true, align = TextAlign.Center)
        }
    }
}

@Composable
fun PageIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(Color.White.copy(0.45f), style = Stroke(1.5.dp.toPx())) }
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(0.5f)))
        Canvas(Modifier.size(width = 10.dp, height = 8.dp)) {
            val w = 1.5.dp.toPx()
            listOf(0.15f, 0.5f, 0.85f).forEach { y ->
                drawLine(Color.White.copy(0.7f), Offset(0f, size.height * y), Offset(size.width, size.height * y), w, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun SearchGlyph() = Canvas(Modifier.size(20.dp)) {
    val s = 2.dp.toPx()
    drawCircle(Color.White, size.width * 0.34f, Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(s))
    drawLine(Color.White, Offset(size.width * 0.67f, size.height * 0.67f),
        Offset(size.width * 0.92f, size.height * 0.92f), s, StrokeCap.Round)
}

@Composable
fun ClockGlyph(modifier: Modifier) = Canvas(modifier) {
    val s = 1.4.dp.toPx()
    drawCircle(Color.White, size.minDimension / 2 - s, style = Stroke(s))
    val c = Offset(size.width / 2, size.height / 2)
    drawLine(Color.White, c, Offset(c.x, c.y - size.height * 0.28f), s, StrokeCap.Round)
    drawLine(Color.White, c, Offset(c.x + size.width * 0.22f, c.y), s, StrokeCap.Round)
}

// ---------- Sertar aplicații ----------
@Composable
fun AppDrawer(apps: List<AppInfo>, onClose: () -> Unit, onAppLongPress: (AppInfo) -> Unit) {
    val wp = LocalWallpaper.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize()) {
        if (wp != null) {
            Image(wp.blurred, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High)
            Box(Modifier.fillMaxSize().background(Color(0xB30B0E14)))
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xE60B0E14)))
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            GlassBox(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search apps", 15.sp, White70)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.intent.component?.flattenToString() ?: it.pkg }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppIcon(app, true, onAppLongPress)
                    }
                }
            }
        }
    }
}

// ---------- Meniuri ----------
@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun MenuItem(label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val color = when {
        !enabled -> Color.White.copy(0.35f)
        danger -> Color(0xFFFF6B6B)
        else -> Color.White
    }
    Text(
        label, 15.sp, color,
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 14.dp)
    )
}

@Composable
fun AppMenu(
    app: AppInfo, pinned: Boolean, inDock: Boolean, dockFull: Boolean,
    onPin: () -> Unit, onDock: () -> Unit, onInfo: () -> Unit, onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Scrim(onDismiss) {
        GlassBox(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(28.dp),
            fill = Color(0x66101018)
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(
                    Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(app.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                    Text(app.label, 17.sp, Color.White, weight = FontWeight.Medium)
                }
                MenuItem(if (pinned) "Remove from home" else "Add to home", onClick = onPin)
                when {
                    inDock -> MenuItem("Remove from dock", onClick = onDock)
                    dockFull -> MenuItem("Add to dock (full)", enabled = false) {}
                    else -> MenuItem("Add to dock", onClick = onDock)
                }
                MenuItem("App info", onClick = onInfo)
                if (!app.isSystem) MenuItem("Uninstall", danger = true, onClick = onUninstall)
            }
        }
    }
}

@Composable
fun HomeMenu(onPickWallpaper: () -> Unit, onSystemWallpaper: () -> Unit, onDismiss: () -> Unit) {
    Scrim(onDismiss) {
        GlassBox(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(28.dp),
            fill = Color(0x66101018)
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text("Home", 17.sp, Color.White, Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    weight = FontWeight.Medium)
                MenuItem("Choose wallpaper", onClick = onPickWallpaper)
                MenuItem("Use system wallpaper", onClick = onSystemWallpaper)
            }
        }
    }
}

// ---------- Text helper ----------
@Composable
fun Text(
    text: String, size: TextUnit, color: Color,
    modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal,
    shadow: Boolean = false, maxLines: Int = 1, align: TextAlign? = null
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight,
            textAlign = align ?: TextAlign.Unspecified,
            shadow = if (shadow) Shadow(Color.Black.copy(0.5f), Offset(0f, 1f), 3f) else null
        )
    )
}

private fun fmt(pattern: String, d: Date) = SimpleDateFormat(pattern, Locale.getDefault()).format(d)
