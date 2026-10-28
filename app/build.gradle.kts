import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing key, from local.properties or environment variables (never committed):
 *   beandoc.keystore / BEANDOC_KEYSTORE                    path to the .jks file
 *   beandoc.keystore.password / BEANDOC_KEYSTORE_PASSWORD
 *   beandoc.key.alias / BEANDOC_KEY_ALIAS
 *   beandoc.key.password / BEANDOC_KEY_PASSWORD
 * Without them, assembleRelease produces an unsigned APK.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(property: String, env: String): String? =
    localProperties.getProperty(property) ?: System.getenv(env)

val releaseKeystore = signingValue("beandoc.keystore", "BEANDOC_KEYSTORE")

android {
    namespace = "com.majeedar.beandoc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.majeedar.beandoc"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("beandoc.keystore.password", "BEANDOC_KEYSTORE_PASSWORD")
                keyAlias = signingValue("beandoc.key.alias", "BEANDOC_KEY_ALIAS")
                keyPassword = signingValue("beandoc.key.password", "BEANDOC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Phones are ARM; x86 libraries only serve emulators and would add ~13 MB to the download.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    packaging {
        // Compress native libraries in the APK: smaller download over weak mobile data,
        // at the cost of extracting them once on install.
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite" // model must be stored uncompressed so it can be memory-mapped
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.litert)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
