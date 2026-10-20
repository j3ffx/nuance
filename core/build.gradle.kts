// Pure Kotlin: colour science, LUT baking, profiles, the built-in test. No platform API, ever:
// the JVM target runs the tests, the iOS targets prove it stays portable (compiled on the CI's macOS job).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// `-Pnuance.report=<dir>` makes the JVM tests write the measurement report (docs/ALGORITHMS.md data)
// into that directory; `-Pnuance.updateGolden=true` rewrites the golden files instead of comparing.
tasks.withType<Test>().configureEach {
    val report = providers.gradleProperty("nuance.report").orElse("")
    val golden = providers.gradleProperty("nuance.updateGolden").orElse("false")
    inputs.property("nuance.report", report)
    inputs.property("nuance.updateGolden", golden)
    systemProperty("nuance.report", report.get())
    systemProperty("nuance.updateGolden", golden.get())
    systemProperty("nuance.root", rootDir.absolutePath)
}
