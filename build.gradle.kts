plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pe.nick.alertabloqueo"
    compileSdk = 34

    defaultConfig {
        applicationId = "pe.nick.alertabloqueo"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Firma fija: así cada nueva versión se instala encima de la anterior sin desinstalar.
    signingConfigs {
        create("propia") {
            storeFile = file("alerta.keystore")
            storePassword = "alertabloqueo"
            keyAlias = "alerta"
            keyPassword = "alertabloqueo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("propia")
        }
        debug {
            signingConfig = signingConfigs.getByName("propia")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
