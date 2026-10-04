plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from the environment (design §16.5): CI restores the upload
// keystore from a secret; a developer machine has none and falls back to the debug key.
val uploadKeystore: String? = System.getenv("TS_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.tamilscripture.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tamilscripture.app"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("TS_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("TS_VERSION_NAME")?.removePrefix("v") ?: "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEV_PACKS_BASE", "\"\"")
    }

    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("TS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TS_KEY_ALIAS")
                keyPassword = System.getenv("TS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // ./gradlew installDebug -PdevPacksBase=http://127.0.0.1:8790/ with tools/serve-packs.py and adb reverse.
            val devPacks = (project.findProperty("devPacksBase") as String?).orEmpty()
            buildConfigField("String", "DEV_PACKS_BASE", "\"$devPacks\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            // AGP refuses APK splits while building a bundle; the bundle splits by ABI itself.
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:media"))
    implementation(project(":feature:home"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:plans"))
    implementation(project(":feature:study"))
    implementation(project(":feature:search"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
