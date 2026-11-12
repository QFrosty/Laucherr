package com.example.glasslauncher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class AppInfo(
    val label: String,
    val pkg: String,
    val intent: Intent,
    val icon: ImageBitmap,
    val isSystem: Boolean
)

class Wallpaper(val sharp: ImageBitmap, val blurred: ImageBitmap)

val pinnedDefaults = listOf(
    "com.instagram.android", "com.zhiliaoapp.musically", "com.snapchat.android",
    "org.telegram.messenger", "com.discord"
)

const val MAX_HOME = 16
const val MAX_DOCK = 4

/** Salvează aplicațiile fixate pe home și în dock (liste de nume de pachet). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private fun get(key: String): List<String>? =
        sp.getString(key, null)?.split(",")?.filter { it.isNotBlank() }
    private fun set(key: String, l: List<String>) = sp.edit().putString(key, l.joinToString(",")).apply()

    fun pinned() = get("pinned")
    fun setPinned(l: List<String>) = set("pinned", l)
    fun dock() = get("dock")
    fun setDock(l: List<String>) = set("dock", l)
}

suspend fun loadApps(ctx: Context): List<AppInfo> = withContext(Dispatchers.Default) {
    val pm = ctx.packageManager
    val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(main, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            val ai = it.activityInfo
            AppInfo(
                label = it.loadLabel(pm).toString(),
                pkg = ai.packageName,
                intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
                isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** Dock implicit: Cameră, Telefon, Mesaje, Google/Browser. */
suspend fun computeDefaultDock(ctx: Context, apps: List<AppInfo>): List<String> =
    withContext(Dispatchers.Default) {
        val pm = ctx.packageManager
        fun pkgOf(i: Intent): String? = try {
            pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) { null }
        val google = "com.google.android.googlequicksearchbox"
        listOfNotNull(
            pkgOf(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            pkgOf(Intent(Intent.ACTION_DIAL)),
            pkgOf(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
            if (apps.any { it.pkg == google }) google
            else pkgOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        ).filter { p -> apps.any { it.pkg == p } }.distinct()
    }

// ---------- Wallpaper ----------
private fun wallpaperFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

/** Blur "ieftin" pe toate versiunile de Android: reducere repetată + upscale cu filtrare. */
private fun blurBitmap(src: Bitmap): Bitmap {
    var b = src
    while (b.width > 96 && b.height > 96) {
        b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
    }
    return b
}

suspend fun loadWallpaper(ctx: Context): Wallpaper? = withContext(Dispatchers.IO) {
    try {
        val f = wallpaperFile(ctx)
        val bmp: Bitmap = if (f.exists()) {
            BitmapFactory.decodeFile(f.path) ?: return@withContext null
        } else {
            val d = WallpaperManager.getInstance(ctx).drawable ?: return@withContext null
            val s = minOf(1f, 1600f / maxOf(d.intrinsicWidth, d.intrinsicHeight, 1))
            d.toBitmap(maxOf(1, (d.intrinsicWidth * s).toInt()), maxOf(1, (d.intrinsicHeight * s).toInt()))
        }
        Wallpaper(bmp.asImageBitmap(), blurBitmap(bmp).asImageBitmap())
    } catch (e: Exception) { null }
}

suspend fun saveWallpaper(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1920) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@withContext false
        FileOutputStream(wallpaperFile(ctx)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) { false }
}

fun clearCustomWallpaper(ctx: Context) { wallpaperFile(ctx).delete() }
