# 星云轨道交通 Android

离线查询线路首末班、计划到发、换乘方向与下两班车。可以按当前手机时间查看倒计时，也可以输入时间预览。到发结果按运营时刻表推算。

## 构建

需要 JDK 17 和 Android SDK 37。Windows 上运行：

    .\gradlew.bat :app:assembleDebug

默认构建使用合成示例线。若持有原始演示网页，可将单个 HTML 放入本地 `demoWeb` 目录，再运行：

    .\gradlew.bat :app:assembleDebug -PprivateTimetable=true

私有构建会把网页中的线路数据和标识打入 APK。该 APK 仅用于本地使用。公开分发请使用默认构建。用户也可在安装后通过首页导入本地 JSON，格式见 `app/src/main/web/sample-line.json`。

## 界面

应用使用 Android WebView 复用示例页面的布局和查询逻辑。底栏与主要按钮使用 Jetpack Compose 和 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的原生组件。Android 13（API 33）起使用完整折射，旧设备使用库支持的简化材质。

已固定 Backdrop 依赖为 2.0.1。上游示例组件来自提交 [eb4a0ea](https://github.com/Kyant0/AndroidLiquidGlass/commit/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5)，许可证见 THIRD-PARTY-LICENSE。
