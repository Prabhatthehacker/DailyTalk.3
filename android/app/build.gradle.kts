import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Resolution order: -P property, local.properties, environment variable, default.
fun cfg(name: String, default: String = ""): String =
    listOf(project.findProperty(name) as String?, localProps.getProperty(name), System.getenv(name))
        .firstOrNull { !it.isNullOrBlank() } ?: default
fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.dailytalk.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dailytalk.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_API_KEY", q(cfg("FIREBASE_API_KEY", "unset")))
        buildConfigField("String", "FIREBASE_APP_ID", q(cfg("FIREBASE_APP_ID", "1:0:android:unset")))
        buildConfigField("String", "FIREBASE_DATABASE_URL", q(cfg("FIREBASE_DATABASE_URL", "https://unset.firebaseio.com")))
        buildConfigField("String", "FIREBASE_PROJECT_ID", q(cfg("FIREBASE_PROJECT_ID", "unset")))
        buildConfigField("String", "GIPHY_API_KEY", q(cfg("GIPHY_API_KEY")))
        buildConfigField("String", "UPLOAD_URL", q(cfg("UPLOAD_URL")))
        buildConfigField("String", "MAX_UPLOAD_MB", q(cfg("MAX_UPLOAD_MB", "200")))
        buildConfigField("String", "TURN_URL", q(cfg("TURN_URL")))
        buildConfigField("String", "TURN_USERNAME", q(cfg("TURN_USERNAME")))
        buildConfigField("String", "TURN_CREDENTIAL", q(cfg("TURN_CREDENTIAL")))
    }

    buildTypes {
        debug { applicationIdSuffix = "" }
        release {
            isMinifyEnabled = false
            // Release signing is intentionally NOT configured here. See README "Production release".
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.getstream:stream-webrtc-android:1.1.3")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
