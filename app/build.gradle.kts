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
        // En GitHub Actions el número de versión sube solo (VERSION_CODE / VERSION_NAME); en local usa estos valores.
        versionCode = (System.getenv("VERSION_CODE") ?: "12").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.7.0"
        // Dirección del archivo update.json que lee el actualizador dentro de la app (vacío = sin actualizaciones)
        buildConfigField("String", "UPDATE_URL", "\"${System.getenv("UPDATE_URL") ?: ""}\"")
    }
    // Llave fija para que el SHA-1 no cambie entre compilaciones (necesario para "Continuar con Google")
    signingConfigs {
        create("drix") {
            storeFile = file("drix.keystore"); storePassword = "drixdebug"
            keyAlias = "drix"; keyPassword = "drixdebug"
        }
    }
    signingConfigs {
        // Versión release firmada. Con los secretos de GitHub usa TU llave privada; sin ellos usa la misma llave
        // de arriba (así las instalaciones actuales se pueden actualizar sin desinstalar).
        create("release") {
            storeFile = file(System.getenv("RELEASE_KEYSTORE")?.takeIf { it.isNotBlank() } ?: "drix.keystore")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "drixdebug"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "drix"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "drixdebug"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("drix") }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // Optimización: R8 reduce y ofusca el código y quita recursos sin usar (APK más liviano y rápido).
            // Si algún día algo falla solo en release, compila con -PnoMinify para descartar R8.
            val optimize = !project.hasProperty("noMinify")
            isMinifyEnabled = optimize
            isShrinkResources = optimize
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.work:work-runtime-ktx:2.9.1") // revisiones en segundo plano (avisos del admin y actualizaciones con la app cerrada)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.osmdroid:osmdroid-android:6.1.20") // mapa real OpenStreetMap (sin API key)
}
