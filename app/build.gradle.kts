plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lscardinali.trill"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.lscardinali.trill"
        // 29+: MediaMuxer Ogg output + platform Opus encoder
        minSdk = 29
        targetSdk = 37
        // CI passes these from the git tag / run number; local builds fall back to dev values.
        versionCode = (findProperty("trill.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("trill.versionName") as String?) ?: "0.0.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release key comes from the environment (GitHub secrets in CI). The same key must sign every
    // release, otherwise Obtainium/Android refuse to update the installed app.
    val releaseKeystore = System.getenv("TRILL_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("TRILL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TRILL_KEY_ALIAS") ?: "trill"
                keyPassword = System.getenv("TRILL_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // Whole-app R8 (a partial packageScope broke OkHttp → Kotlin stdlib access at runtime).
            // Reflection-heavy deps (NewPipe Extractor / Rhino) are kept in src/main/keepRules.
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}