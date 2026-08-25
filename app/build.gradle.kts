import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.newagedevs.gesturevolume"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.newagedevs.gesturevolume"
        minSdk = 26
        targetSdk = 37
        versionCode = 29
        versionName = "1.2.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Splashscreen
    implementation(libs.androidx.core.splashscreen)

    // Icons
    implementation(libs.androidx.compose.material.icons.extended)

    // Lottie
    implementation(libs.lottie.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil)

    // Sheets
    implementation(libs.color)

    // Billing
    implementation(libs.billing.ktx)

    // Play Core
    implementation(libs.play.update)
    implementation(libs.play.update.ktx)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    // Ads
    implementation(libs.applovin.sdk)
    implementation(libs.play.services.base)
    implementation(libs.inmobi.adapter)
    implementation(libs.mintegral.adapter)
    implementation(libs.picasso)
    implementation(libs.androidx.recyclerview)
    implementation(libs.vungle.adapter)
    implementation(libs.facebook.adapter)
    implementation(libs.unityads.adapter)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}