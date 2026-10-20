plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.spotless)
}

// One formatter for every Kotlin file and build script; ktlint reads its rules from .editorconfig.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        // Sources only: walking build/ races with tasks that write there in parallel.
        target("*/src/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("*.kts", "*/*.kts")
        ktlint(ktlintVersion)
    }
}
