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
        versionCode = 12
        versionName = "0.10.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Widget della schermata Home (Material You)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Aggiornamento periodico in background
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Lettura referti: testo da PDF (anche protetti) + OCR on-device per scansioni e foto
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")

    // Test end-to-end su emulatore (dati sintetici in Connessione Salute + screenshot reali)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Rendering degli schermi per la revisione grafica (solo con -Pshots, non entra nell'APK)
if (project.hasProperty("shots")) {
    apply(plugin = "app.cash.paparazzi")
    android.sourceSets.getByName("test").java.srcDir("src/shots/java")
}
