import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Servidor de avisos: backend.properties, que se puede sobrescribir para pruebas con -PreportsApiUrl.
val backend = Properties().apply { rootProject.file("backend.properties").inputStream().use { load(it) } }
val reportsUrl = (findProperty("reportsApiUrl") as String?) ?: backend.getProperty("REPORTS_API_URL", "")
val reportsKey = (findProperty("reportsApiKey") as String?) ?: backend.getProperty("REPORTS_API_KEY", "")

android {
    namespace = "com.blueway.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.blueway.app"
        minSdk = 26
        targetSdk = 35
        // Minutos desde sep. 2026: siempre crece, tanto en el PC como en CI, y cada APK actualiza al anterior.
        versionCode = ((System.currentTimeMillis() / 1000 - 1_790_000_000L) / 60).toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("String", "REPORTS_API_URL", "\"$reportsUrl\"")
        buildConfigField("String", "REPORTS_API_KEY", "\"$reportsKey\"")
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
        buildConfig = true
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
