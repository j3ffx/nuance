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
