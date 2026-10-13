package com.example.pluginization.plugin

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * 代理系统 Instrumentation：
 * - newActivity：遇到占位 Activity 且携带插件类名时，用插件 ClassLoader 创建真实的插件 Activity；
 * - callActivityOnCreate：在 onCreate 之前把 Activity 的 Resources / Theme 换成插件的。
 */
class PluginInstrumentation(private val base: Instrumentation) : Instrumentation() {

    override fun newActivity(cl: ClassLoader, className: String, intent: Intent?): Activity {
        val target = intent?.getStringExtra(PluginManager.EXTRA_PLUGIN_ACTIVITY)
        if (className == PluginHostActivity::class.java.name && target != null) {
            Log.d(TAG, "newActivity: $className -> $target")
            return PluginManager.classLoader.loadClass(target)
                .getDeclaredConstructor().newInstance() as Activity
        }
        return base.newActivity(cl, className, intent)
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        if (activity.javaClass.classLoader === PluginManager.classLoader) {
            PluginManager.injectResources(activity)
        }
        base.callActivityOnCreate(activity, icicle)
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?, persistentState: android.os.PersistableBundle?) {
        if (activity.javaClass.classLoader === PluginManager.classLoader) {
            PluginManager.injectResources(activity)
        }
        base.callActivityOnCreate(activity, icicle, persistentState)
    }

    private companion object {
        const val TAG = "PluginInstrumentation"
    }
}
