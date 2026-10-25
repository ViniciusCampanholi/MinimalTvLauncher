package com.minimal.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.minimal.tvlauncher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val packageManager: PackageManager = context.packageManager

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val selectedPackages = getSelectedPackages()

        // Busca apps otimizados para Android TV (Leanback) e apps Android padrão
        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val leanbackApps = packageManager.queryIntentActivities(leanbackIntent, 0)
        val standardApps = packageManager.queryIntentActivities(standardIntent, 0)

        val uniquePackages = linkedMapOf<String, ResolveInfo>()
        for (info in leanbackApps + standardApps) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg != context.packageName && !uniquePackages.containsKey(pkg)) {
                uniquePackages[pkg] = info
            }
        }

        uniquePackages.map { (pkg, resolveInfo) ->
            val appLabel = resolveInfo.loadLabel(packageManager)?.toString() ?: pkg
            val iconDrawable = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
            val bannerDrawable = runCatching {
                resolveInfo.activityInfo.loadBanner(packageManager)
                    ?: packageManager.getApplicationBanner(pkg)
            }.getOrNull()

            AppInfo(
                packageName = pkg,
                label = appLabel,
                icon = iconDrawable?.toImageBitmap(maxWidth = 192, maxHeight = 192),
                banner = bannerDrawable?.toImageBitmap(maxWidth = 320, maxHeight = 180),
                isSelected = selectedPackages.contains(pkg)
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun toggleAppSelection(packageName: String): Set<String> {
        val current = getSelectedPackages().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.edit().putStringSet(KEY_SELECTED_PACKAGES, current).apply()
        return current
    }

    fun launchApp(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)

        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(it) }
        }
    }

    fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun Drawable.toImageBitmap(maxWidth: Int, maxHeight: Int): ImageBitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap.asImageBitmap()
        }
        val width = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(maxWidth) else maxWidth
        val height = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(maxHeight) else maxHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap.asImageBitmap()
    }

    companion object {
        private const val PREFS_NAME = "minimal_tv_launcher_prefs"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
    }
}
