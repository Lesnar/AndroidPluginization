# AndroidPluginization

`app` 是宿主（主工程），`stub` 是插件（子工程）。点击宿主首页的 **Open Plugin (Stub MainActivity)** 按钮，通过插件化技术打开 `stub` 中的 `MainActivity`（Compose 页面，显示 "Hello Android!"）。

## 实现原理

```
[宿主 FirstFragment 按钮]
   └─ PluginManager.startPluginActivity("com.example.stub.MainActivity")
        ├─ init: assets/stub.apk -> codeCacheDir(只读) -> DexClassLoader
        │        PackageManager.getResourcesForApplication -> 插件 Resources
        │        反射替换 ActivityThread.mInstrumentation 为 PluginInstrumentation
        └─ startActivity(占位 PluginHostActivity + extra=插件类名)
             └─ AMS 校验通过(占位 Activity 已在宿主 Manifest 注册)
                  └─ PluginInstrumentation.newActivity: 用插件 ClassLoader 创建真实 Activity
                  └─ PluginInstrumentation.callActivityOnCreate: 注入插件 Resources / Theme
```

这是经典的"占位 Activity + Hook Instrumentation"方案，插件 Activity 无需任何改动。

## 操作记录

1. **分析工程**：`settings.gradle.kts` 已包含 `:app`、`:stub`；`stub` 是独立 application 模块（Compose），其 `MainActivity` 继承 `ComponentActivity`，因此不能用"代理 Activity 转发生命周期"的方案（Compose 需要真实 Window），选择 Hook Instrumentation 方案。
2. **构建期打包插件**：修改 `app/build.gradle.kts`，新增 `CopyStubApkTask`，并在 `androidComponents.onVariants` 中注册，依赖 `:stub:assembleDebug`，把 stub 的 APK 复制为宿主 `assets/stub.apk`（通过 `addGeneratedSourceDirectory` 加入 assets）。
3. **新增占位 Activity**：`app/.../plugin/PluginHostActivity.kt`，并在 `app/src/main/AndroidManifest.xml` 注册（`exported=false`）。
4. **新增 `PluginInstrumentation`**：重写 `newActivity`（加载插件类）和 `callActivityOnCreate`（注入资源）。
5. **新增 `PluginManager`**：释放 APK 并 `setReadOnly()`（Android 14+ 要求动态加载的 dex 只读）、创建 `DexClassLoader`、创建插件 `Resources`、Hook `Instrumentation`、提供 `startPluginActivity`。
6. **首页按钮**：`fragment_first.xml` 新增 `button_plugin`，`strings.xml` 新增 `open_plugin`，`FirstFragment.kt` 中点击调用 `PluginManager.startPluginActivity(requireContext(), "com.example.stub.MainActivity")`。
7. **构建**：`./gradlew :app:assembleDebug` 成功。
8. **真机验证（遇到并解决的问题）**：
   - 第一次点击崩溃：反射 `ContextThemeWrapper.mTheme` 被隐藏 API 限制拒绝（`max-target-p: denied`）。
   - 修复：不再反射 `mTheme`，改为 `activity.theme.setTo(pluginTheme)` 把插件主题拷贝进已有 Theme；`mResources`、`ActivityThread.mInstrumentation` 属 unsupported 名单，仍可反射。
   - 重新安装后点击按钮，成功显示插件页面 "Hello Android!"，logcat 可见 `newActivity: PluginHostActivity -> com.example.stub.MainActivity`。

## 运行

```bash
./gradlew :app:installDebug   # 会自动先构建 :stub 并打入 assets
```
打开 App，点击首页的 "Open Plugin (Stub MainActivity)" 按钮。

## 已知限制

- 仅演示单个插件 Activity；插件未在宿主 Manifest 声明的其他组件（Service、Receiver 等）未处理。
- 依赖少量隐藏 API 反射（`ActivityThread`、`ContextThemeWrapper.mResources`），未来 Android 版本可能受限。
- 插件的 androidx 依赖与宿主同名类以宿主为准（父类加载器优先）。
- 插件 APK 使用 debug 构建并随宿主打包。
