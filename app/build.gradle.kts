import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

/**
 * Signing credentials, read from `local.properties` first and the environment second.
 *
 * `local.properties` is gitignored and already holds this project's other secrets, so the keystore
 * password never reaches version control. The environment fallback is what lets CI sign without a
 * local.properties file at all.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSecret(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSecret("RELEASE_STORE_FILE")
val releaseStorePassword = signingSecret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingSecret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingSecret("RELEASE_KEY_PASSWORD")

// All four, or none. A half-configured signing config fails deep inside the packaging task with a
// message that does not mention the missing property, so it is checked up front instead.
val hasReleaseSigning = releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null &&
        file(releaseStoreFile).exists()

android {
    namespace = "com.newagedevs.gesturevolume"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.newagedevs.gesturevolume"
        minSdk = 26
        targetSdk = 37
        versionCode = 34
        versionName = "1.3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Both schemes: v2 is what Play requires, v1 keeps the artifact installable by
                // anything still verifying the old way.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Left unsigned when the credentials are absent rather than failing the build, so a
            // clone without the keystore can still run assembleRelease to check that R8 passes.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
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