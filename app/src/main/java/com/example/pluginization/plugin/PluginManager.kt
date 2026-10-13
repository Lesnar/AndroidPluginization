package com.example.pluginization.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.res.Resources
import android.os.Build
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 极简插件管理器：
 * 1. 把 assets 中的插件 APK 释放到私有目录（并设为只读，Android 14+ 对动态加载 dex 的要求）；
 * 2. 用 DexClassLoader 加载插件代码；
 * 3. 通过 PackageManager 为插件 APK 创建 Resources；
 * 4. Hook ActivityThread 的 Instrumentation，让占位 Activity 被替换为插件 Activity。
 */
object PluginManager {
    private const val TAG = "PluginManager"
    private const val PLUGIN_ASSET = "stub.apk"
    const val EXTRA_PLUGIN_ACTIVITY = "plugin_activity_class"

    private var initialized = false
    private lateinit var pluginInfo: PackageInfo

    lateinit var classLoader: DexClassLoader
        private set
    lateinit var resources: Resources
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext

        val apk = File(app.codeCacheDir, "plugins/stub.apk")
        apk.parentFile!!.mkdirs()
        if (apk.exists()) {
            apk.setWritable(true)
            apk.delete()
        }
        app.assets.open(PLUGIN_ASSET).use { input -> apk.outputStream().use { input.copyTo(it) } }
        apk.setReadOnly()

        val optDir = File(app.codeCacheDir, "plugins/odex").apply { mkdirs() }
        classLoader = DexClassLoader(apk.absolutePath, optDir.absolutePath, null, app.classLoader)

        pluginInfo = app.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: error("无法解析插件 APK")
        pluginInfo.applicationInfo!!.apply {
            sourceDir = apk.absolutePath
            publicSourceDir = apk.absolutePath
        }
        resources = app.packageManager.getResourcesForApplication(pluginInfo.applicationInfo!!)

        hookInstrumentation()
        initialized = true
        Log.d(TAG, "plugin loaded: ${pluginInfo.packageName}")
    }

    /** 启动插件中的 Activity（类名为插件内的完整类名）。 */
    fun startPluginActivity(context: Context, className: String) {
        init(context)
        val intent = Intent().setClassName(context.packageName, PluginHostActivity::class.java.name)
            .putExtra(EXTRA_PLUGIN_ACTIVITY, className)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 把插件 Activity 的 Resources 和 Theme 替换为插件自己的。 */
    fun injectResources(activity: Activity) {
        val wrapperClass = android.view.ContextThemeWrapper::class.java
        wrapperClass.getDeclaredField("mResources").apply { isAccessible = true }.set(activity, resources)
        // mTheme 属于受限的隐藏 API，改为把插件主题拷贝进 Activity 已有的 Theme
        val pluginTheme = resources.newTheme().apply {
            val themeId = pluginInfo.applicationInfo!!.theme
            if (themeId != 0) applyStyle(themeId, true)
        }
        activity.theme.setTo(pluginTheme)
    }

    private fun hookInstrumentation() {
        val atClass = Class.forName("android.app.ActivityThread")
        val thread = atClass.getDeclaredMethod("currentActivityThread").apply { isAccessible = true }.invoke(null)
        val field = atClass.getDeclaredField("mInstrumentation").apply { isAccessible = true }
        val base = field.get(thread) as android.app.Instrumentation
        if (base !is PluginInstrumentation) field.set(thread, PluginInstrumentation(base))
    }
}
