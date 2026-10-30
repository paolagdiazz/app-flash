plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.blueway.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.blueway.app"
        minSdk = 26
        targetSdk = 35
        // CI usa el número de ejecución para que cada APK actualice al anterior sin desinstalar.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(1) ?: 1
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Firma de desarrollo compartida (no secreta): permite actualizar entre builds locales y de CI.
    // No sirve para publicar en Google Play.
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("keystore/blueway-dev.jks")
            storePassword = "blueway-dev"
            keyAlias = "blueway"
            keyPassword = "blueway-dev"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            signingConfig = signingConfigs.getByName("dev")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.maplibre)
    testImplementation(libs.junit)
}
