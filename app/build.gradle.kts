plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.kontrolbot" // ganti sesuai package app Anda
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("com.google.android.gms:play-services-auth:20.5.0") // Google Sign-In
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
