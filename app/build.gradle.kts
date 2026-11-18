import java.util.Base64

plugins {
    id("com.android.application") version "9.3.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

val privateData = providers.gradleProperty("privateTimetable").map(String::toBooleanStrict).orElse(false)
val generated = layout.buildDirectory.dir("generated/timetable")
val prepareTimetable by tasks.registering {
    inputs.property("privateTimetable", privateData)
    inputs.file("src/main/sample/lines.json")
    inputs.files(privateData.map { enabled ->
        if (enabled) rootProject.fileTree("demoWeb") { include("*.html") }.files else emptySet()
    })
    outputs.dir(generated)
    doLast {
        val output = generated.get().asFile.apply { mkdirs() }
        output.resolve("logo.png").delete()
        if (privateData.get()) {
            val sources = rootProject.file("demoWeb").listFiles { file -> file.extension == "html" }.orEmpty()
            require(sources.size == 1) { "Place one private demo HTML file in demoWeb." }
            val source = sources.single().readText()
            fun value(name: String): String {
                val pattern = Regex("^const " + name + "\\s*=\\s*(.*?);?\\s*$", RegexOption.MULTILINE)
                return pattern.find(source)?.groupValues?.get(1)?.removeSuffix(";")
                    ?: error("Missing " + name)
            }
            val main = value("MAIN_DATA")
            val others = value("BUNDLED_LINES").trim().removePrefix("[").removeSuffix("]")
            output.resolve("lines.json").writeText("[" + main + (if (others.isBlank()) "" else "," + others) + "]")
            val logo = value("APP_LOGO").trim('"', '\'')
            if (logo.startsWith("data:image/png;base64,")) {
                output.resolve("logo.png").writeBytes(Base64.getDecoder().decode(logo.substringAfter(",")))
            }
        } else {
            file("src/main/sample/lines.json").copyTo(output.resolve("lines.json"), overwrite = true)
        }
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
    sourceSets["main"].assets.directories.add(generated.get().asFile.absolutePath)
}
tasks.named("preBuild") { dependsOn(prepareTimetable) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
}
