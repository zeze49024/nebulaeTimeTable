# 星云轨道交通 Android

使用 Kotlin 与 Jetpack Compose 原生实现离线轨道交通时刻表。页面、导航、搜索、倒计时、时间选择、文件导入及查询计算均在安卓端执行。

支持首页线路目录、各站各终点首末班、线路与车站选择、同名换乘站联查、各终点下两班计划列车、实时倒计时、模拟查询时间，以及 JSON 线路导入和本地持久化。时间查询沿用示例的凌晨四点运营日边界、90 分钟查询窗口和首末班 85 秒容差。

## 界面与适配

界面沿用示例的品牌红、线路颜色、内容顺序及页面结构。玻璃底栏、返回及主要操作按钮使用 AndroidLiquidGlass。手机以单列展示，内容宽度达到 700 dp 时首末班和到发卡片采用双列，内容最大宽度 1120 dp。通过系统 WindowInsets 处理状态栏、屏幕切口、手势区和键盘。

最低 Android 6.0。Android 13 起可使用完整运行时着色效果，旧系统使用库提供的降级效果。

## 构建

需要 JDK 17 或更新的兼容 JDK、Android SDK 37。

    .\gradlew.bat :app:assembleDebug
    .\gradlew.bat :app:testDebugUnitTest :app:lintDebug

默认构建只打包合成示例线。持有原始示例网页时，将单个 HTML 放入本地 demoWeb 目录，然后运行：

    .\gradlew.bat :app:assembleDebug -PprivateTimetable=true

该构建任务只提取线路 JSON 和 PNG 标识供原生程序读取。含私有时刻表的 APK 存放本地，公开发布使用默认构建。demoWeb、private 及生成产物已加入 Git 忽略。

导入格式见 app/src/main/sample/lines.json 中的线路对象。一次导入一个对象。重复线路 ID 会提示失败。

## 第三方代码

Backdrop 依赖固定为 2.0.1。玻璃组件及手势工具来自 Kyant0/AndroidLiquidGlass 提交 eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5，保留演示的光学与交互数值。LiquidButton 增加可设置的内边距参数。Coroutines.kt 使用安卓 awaitFrame 的显式别名。上游许可证见 THIRD-PARTY-LICENSE。
