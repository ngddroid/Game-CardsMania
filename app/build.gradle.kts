plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Firebase: reads app/google-services.json (Firebase console → Project settings → your Android app)
    alias(libs.plugins.google.services)
    // uploads R8 mapping files for release builds so crash reports show real class and method names
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.ngdstudio.mindimania"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Play Store app ID: permanent once uploaded. The Kotlin code uses the same package (namespace).
        applicationId = "com.ngdstudio.mindimania"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // a call that needs a newer Android than minSdk crashes older phones (Mindi on Android 11 did):
        // stop the release build instead
        fatal += "NewApi"
    }
    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG: debug-only shortcuts such as the fully open Collection
    }
    androidResources {
        // SoundPool / MediaPlayer open res/raw sounds as file descriptors, which only works for files stored
        // uncompressed in the APK. FLAC is already compressed, so zipping it again gains nothing anyway.
        noCompress += "flac"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    // installs the baseline profile (src/main/baseline-prof.txt) on sideloaded / non-Play installs too
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    // daily reminder notifications
    implementation(libs.androidx.work.runtime.ktx)
    // Google Play in-app updates
    implementation(libs.play.app.update.ktx)
    // app-update pulls in Fragment 1.1.0, too old for registerForActivityResult (release lint stops the build)
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
