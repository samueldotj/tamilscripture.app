plugins {
    alias(libs.plugins.android.library)
}

// libts_mobile.so and its Kotlin bindings are built from the website repo by
// scripts/build-rust.sh (design §9); this module only packages them.
android {
    namespace = "com.tamilscripture.core.rust"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") {
    val bindings = file("src/main/kotlin/uniffi/ts_mobile/ts_mobile.kt")
    doFirst {
        check(bindings.exists()) { "Run scripts/build-rust.sh first: Rust bindings are missing (${bindings.path})." }
    }
}

dependencies {
    api("${libs.jna.get().module}:${libs.versions.jna.get()}@aar")
}
