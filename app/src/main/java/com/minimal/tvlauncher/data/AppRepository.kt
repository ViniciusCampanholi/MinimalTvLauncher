package com.minimal.tvlauncher.data

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.minimal.tvlauncher.model.AppInfo
import com.minimal.tvlauncher.ui.WallpaperPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass

class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val packageManager: PackageManager = context.packageManager

    @Volatile
    private var cachedApps: List<AppInfo>? = null

    @Volatile
    private var cachedPackageNames: Set<String> = emptySet()

    suspend fun loadInstalledApps(forceReload: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
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

        val currentCache = cachedApps
        if (!forceReload && currentCache != null && uniquePackages.keys == cachedPackageNames) {
            return@withContext currentCache.map { app ->
                val selected = selectedPackages.contains(app.packageName)
                if (app.isSelected == selected) app else app.copy(isSelected = selected)
            }
        }

        val loaded = uniquePackages.map { (pkg, resolveInfo) ->
            val appLabel = resolveInfo.loadLabel(packageManager).toString().ifBlank { pkg }
            val iconDrawable = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
            val bannerDrawable = runCatching {
                resolveInfo.activityInfo.loadBanner(packageManager)
                    ?: packageManager.getApplicationBanner(pkg)
            }.getOrNull()

            AppInfo(
                packageName = pkg,
                label = appLabel,
                icon = iconDrawable?.toImageBitmap(maxWidth = 144, maxHeight = 144),
                banner = bannerDrawable?.toImageBitmap(maxWidth = 280, maxHeight = 158),
                isSelected = selectedPackages.contains(pkg)
            )
        }.sortedBy { it.label.lowercase() }

        cachedPackageNames = uniquePackages.keys.toSet()
        cachedApps = loaded
        loaded
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

    fun openWifiSettings() {
        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val startedWifi = runCatching { context.startActivity(wifiIntent) }.isSuccess
        if (!startedWifi) {
            val wirelessIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val startedWireless = runCatching { context.startActivity(wirelessIntent) }.isSuccess
            if (!startedWireless) {
                openSystemSettings()
            }
        }
    }

    /**
     * Ativa imediatamente o Protetor de Tela (Screensaver / Daydream / Ambient Mode) do Android TV.
     */
    fun startScreensaver() {
        // 1. Tenta iniciar diretamente a Activity oficial do protetor de tela do Android (Somnambulator)
        val somnambulatorIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(somnambulatorIntent) }.isSuccess) {
            return
        }

        // 2. Tenta acionar o serviço de sistema IDreamManager.dream()
        val dreamedViaManager = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/os/ServiceManager;", "Landroid/service/dreams/")
            }
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "dreams")
            if (binder != null) {
                val stubClass = Class.forName("android.service.dreams.IDreamManager\$Stub")
                val asInterface = stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))
                val dreamManager = asInterface.invoke(null, binder)
                val dreamMethod = dreamManager.javaClass.getMethod("dream")
                dreamMethod.invoke(dreamManager)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (dreamedViaManager) return

        // 3. Fallback: abre as configurações de protetor de tela caso a TV tenha desabilitado o Somnambulator
        val dreamSettings = Intent(Settings.ACTION_DREAM_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!runCatching { context.startActivity(dreamSettings) }.isSuccess) {
            openSystemSettings()
        }
    }

    fun getSelectedWallpaper(): WallpaperPreset {
        val savedId = prefs.getString(KEY_SELECTED_WALLPAPER, WallpaperPreset.PURE_BLACK.id)
        return WallpaperPreset.fromId(savedId)
    }

    fun setSelectedWallpaper(preset: WallpaperPreset) {
        prefs.edit().putString(KEY_SELECTED_WALLPAPER, preset.id).apply()
    }

    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return true
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    @Suppress("DEPRECATION")
    fun promptSetDefaultLauncher(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                val launched = runCatching {
                    activity.startActivityForResult(roleIntent, 1001)
                }.isSuccess
                if (launched) return
            }
        }

        val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openedHomeSettings = runCatching { context.startActivity(homeSettingsIntent) }.isSuccess
        if (!openedHomeSettings) {
            // Dispara o seletor de Home do Android
            val chooserIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooserIntent) }
        }
    }

    private fun Drawable.toImageBitmap(maxWidth: Int, maxHeight: Int): ImageBitmap {
        if (this is BitmapDrawable && bitmap != null) {
            val src = bitmap
            if (src.width <= maxWidth && src.height <= maxHeight) {
                return src.asImageBitmap()
            }
            val scale = minOf(
                maxWidth.toFloat() / src.width.coerceAtLeast(1),
                maxHeight.toFloat() / src.height.coerceAtLeast(1)
            )
            val targetW = (src.width * scale).toInt().coerceAtLeast(1)
            val targetH = (src.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(src, targetW, targetH, true).asImageBitmap()
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
        private const val KEY_SELECTED_WALLPAPER = "selected_wallpaper"
    }
}
