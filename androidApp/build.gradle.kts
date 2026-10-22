import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The version comes from the release tag (-Pnuance.version=0.1.0, see release.yml); local builds are 0.0.0.
val appVersion = (findProperty("nuance.version") as String?) ?: "0.0.0"
val versionParts = appVersion.split('.').map { it.toInt() }
require(versionParts.size == 3) { "nuance.version must be x.y.z, got $appVersion" }

/**
 * The release key: keystore.properties at the repository root on the PC, environment variables
 * in CI (both ignored or secret, never committed). Without either, the release APK is unsigned.
 */
val signing: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        val p = Properties().apply { file.inputStream().use { load(it) } }
        p.stringPropertyNames().associateWith { p.getProperty(it) }
    } else {
        System.getenv("NUANCE_STORE_FILE")?.let { store ->
            mapOf(
                "storeFile" to store,
                "storePassword" to System.getenv("NUANCE_STORE_PASSWORD"),
                "keyAlias" to System.getenv("NUANCE_KEY_ALIAS"),
                "keyPassword" to System.getenv("NUANCE_KEY_PASSWORD")
            )
        }
    }
}

android {
    namespace = "io.github.j3ffx.nuance"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.j3ffx.nuance"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // 0.1.0 → 100, 1.2.3 → 10203: always increasing with the version.
        versionCode = (versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]).coerceAtLeast(1)
        versionName = appVersion
        // Where the update check looks for new versions (GitHub Releases, the only network call).
        buildConfigField("String", "UPDATE_REPOSITORY", "\"j3ffx/nuance\"")
    }

    signingConfigs {
        signing?.let { s ->
            create("release") {
                storeFile = rootProject.file(s.getValue("storeFile"))
                storePassword = s.getValue("storePassword")
                keyAlias = s.getValue("keyAlias")
                keyPassword = s.getValue("keyPassword")
            }
        }
    }

    // Nuance ships APKs with both languages and switches between them in the app: never split them.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        // The dev variant installs next to the real app: own id, own name, marked icon.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Nuance dev")
        }
        release {
            resValue("string", "app_name", "Nuance")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    debugImplementation(libs.compose.ui.tooling)
}
