plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// versionCode: major*10000 + minor*100 + patch  (e.g. 1.4.1 → 10401)
fun versionNameToCode(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() ?: 0 }
    return (parts.getOrElse(0) { 0 } * 10000) +
           (parts.getOrElse(1) { 0 } * 100) +
           (parts.getOrElse(2) { 0 })
}

val appVersionName = project.findProperty("appVersionName") as String? ?: "1.4.1"

android {
    namespace = "com.chituch.audioeditor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chituch.audioeditor"
        minSdk = 26
        targetSdk = 35
        versionCode = versionNameToCode(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("chituch-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "chituch123"
            keyAlias = "chituch"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "chituch123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.core.splashscreen)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
