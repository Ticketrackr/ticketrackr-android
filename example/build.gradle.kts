plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ticketrackr.example"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ticketrackr.example"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":support"))
    implementation("androidx.activity:activity:1.7.0")
    implementation("androidx.core:core:1.13.1")
}
