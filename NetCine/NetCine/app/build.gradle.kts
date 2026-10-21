plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.facu.netcine"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.facu.netcine"
        minSdk = 22          // Fire OS 5 (Fire Stick 1ra/2da gen) en adelante
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Dirección del catálogo de tu proveedor. Si algún día cambia, editá esta línea.
        buildConfigField("String", "BASE_URL", "\"http://10.1.1.219\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmado con la clave de debug: alcanza para instalar por fuera de la tienda.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("androidx.annotation:annotation-experimental:1.4.1")
}
