plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.cinocinorob.bioeta"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.cinocinorob.bioeta"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
    }

    // Chiave fissa: ogni nuova build si installa sopra la precedente senza perdere i dati.
    signingConfigs {
        create("fixed") {
            storeFile = file("bioeta.keystore")
            storePassword = "bioeta-local"
            keyAlias = "bioeta"
            keyPassword = "bioeta-local"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Lettura referti: testo da PDF (anche protetti) + OCR on-device per scansioni e foto
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
