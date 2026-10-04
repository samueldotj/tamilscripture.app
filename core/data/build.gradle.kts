plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tamilscripture.core.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Pack tests (M0-14) call the Rust normaliser through JNA: the host build of ts-mobile
    // (cargo build -p ts-mobile --release in the website repo, or TS_MOBILE_HOST_LIB).
    testOptions.unitTests.all { test ->
        val hostLib = providers.environmentVariable("TS_MOBILE_HOST_LIB")
            .orElse(rootProject.layout.projectDirectory.dir("../tamilscripture.com/target/release").asFile.path)
        test.systemProperty("jna.library.path", hostLib.get())
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:rust"))
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // The desktop build of the same SQLite (FTS5), so pack code runs in JVM tests.
    testImplementation("androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqlite.get()}")
    testImplementation("androidx.work:work-testing:${libs.versions.work.get()}")
    // JNA with its desktop dispatch library (the app ships the Android AAR).
    testImplementation("${libs.jna.get().module}:${libs.versions.jna.get()}")
}
