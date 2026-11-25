// Top-level build file. Versions are pinned to releases current in Nov 2026.
// Requires Gradle 9.6+ and JDK 17 (see gradle/wrapper/gradle-wrapper.properties).
buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin (bundled KGP 2.2.10). Pin the newer KGP so
        // the Compose and serialization compiler plugins use Kotlin 2.2.21.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    }
}

plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}
