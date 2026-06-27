import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.ledgerpop"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.ledgerpop"
        minSdk = 31
        //noinspection OldTargetApi
        targetSdk = 37
        versionCode = 18           // ← increase by 1 from last time (was 17)
        versionName = "4"     // ← change from "3.7" to "4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    signingConfigs {
        create("release") {
            val properties = Properties()
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { properties.load(it) }
            }

            // Prioritize Environment Variables (GitHub CI), then fall back to keystore.properties (Local)
            val sFile = System.getenv("KEYSTORE_FILE") ?: properties.getProperty("storeFile")
            val sPassword = System.getenv("KEYSTORE_PASSWORD") ?: properties.getProperty("storePassword")
            val kAlias = System.getenv("KEY_ALIAS") ?: properties.getProperty("keyAlias")
            val kPassword = System.getenv("KEY_PASSWORD") ?: properties.getProperty("keyPassword")

            if (sFile != null && sPassword != null && kAlias != null && kPassword != null) {
                storeFile = rootProject.file(sFile)
                storePassword = sPassword
                keyAlias = kAlias
                keyPassword = kPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing if configured, otherwise fallback to debug
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.material.icons)
    implementation(libs.haze)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}
