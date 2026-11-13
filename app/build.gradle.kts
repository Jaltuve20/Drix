plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin 2.0+
    id("com.google.gms.google-services")      // Firebase: requiere app/google-services.json
}
android {
    namespace = "com.drix.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.drix.app"; minSdk = 26; targetSdk = 35
        versionCode = 9; versionName = "1.5.3"
    }
    // Llave fija para que el SHA-1 no cambie entre compilaciones (necesario para "Continuar con Google")
    signingConfigs {
        create("drix") {
            storeFile = file("drix.keystore"); storePassword = "drixdebug"
            keyAlias = "drix"; keyPassword = "drixdebug"
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("drix") } }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.osmdroid:osmdroid-android:6.1.20") // mapa real OpenStreetMap (sin API key)
}
