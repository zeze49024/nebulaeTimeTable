plugins {
    id("com.android.application") version "9.3.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

val webSource = layout.projectDirectory.dir("src/main/web")
val privateDemo = rootProject.layout.projectDirectory.dir("demoWeb")
val usePrivateDemo = providers.gradleProperty("privateTimetable").map(String::toBooleanStrict).orElse(false)
val generatedWeb = layout.buildDirectory.dir("generated/railAssets")

val prepareRailAssets by tasks.registering {
    inputs.dir(webSource)
    inputs.property("privateTimetable", usePrivateDemo)
    inputs.files(usePrivateDemo.map { enabled ->
        if (enabled) rootProject.fileTree(privateDemo) { include("*.html") }.files else emptySet()
    })

    outputs.dir(generatedWeb)
    doLast {
        val demo = if (usePrivateDemo.get()) {
            val demoFiles = privateDemo.asFile.listFiles { file -> file.extension == "html" }.orEmpty()
            require(demoFiles.size == 1) { "Place one private source HTML file in demoWeb." }
            demoFiles.single().readText()
        } else {
            null
        }
        fun declaration(name: String, fallback: String): String {
            if (demo == null) return fallback
            val marker = Regex("^const " + name + "\\s*=")
            val line = demo.lineSequence().singleOrNull { marker.containsMatchIn(it.trimStart()) }
                ?: error("Cannot find " + name + " in private demo.")
            return line.trimStart().replaceFirst(marker, "").trim().removeSuffix(";")
        }
        val data = mapOf(
            "%%MAIN_DATA%%" to declaration("MAIN_DATA", webSource.file("sample-line.json").asFile.readText()),
            "%%BUNDLED_LINES%%" to declaration("BUNDLED_LINES", "[]"),
            "%%APP_LOGO%%" to declaration("APP_LOGO", "''"),
        )
        val template = webSource.file("index.html").asFile.readText()
        val merged = Regex("%%(?:MAIN_DATA|BUNDLED_LINES|APP_LOGO)%%").replace(template) {
            data.getValue(it.value)
        }
        generatedWeb.get().asFile.apply { mkdirs() }.resolve("index.html").writeText(merged)
    }
}

android {
    namespace = "cn.nebulae.timetable"
    compileSdk = 37
    defaultConfig {
        applicationId = "cn.nebulae.timetable"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    sourceSets["main"].assets.directories.add(generatedWeb.get().asFile.absolutePath)
}

tasks.named("preBuild") { dependsOn(prepareRailAssets) }

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
}
