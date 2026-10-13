package com.example.pluginization.plugin

import android.app.Activity

/**
 * 占位 Activity：仅在宿主 AndroidManifest 中注册以通过系统校验。
 * 实际运行时 [PluginInstrumentation] 会把它替换为插件中的真实 Activity，因此该类不会被实例化。
 */
class PluginHostActivity : Activity()
