plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.diffplug.spotless") version "8.10.4" apply false
}

allprojects {
    if (path == ":scratch") {
        return@allprojects
    }
    apply(plugin = "com.diffplug.spotless")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
            ktlint("1.8.0").setEditorConfigPath(rootProject.file(".editorconfig").absolutePath)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.8.0")
        }
    }
}
