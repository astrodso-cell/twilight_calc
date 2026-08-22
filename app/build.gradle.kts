import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.twilightcalculator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.twilightcalculator"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (LocalDate/LocalTime/ZoneId) доступен на minSdk 21 через desugaring.
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        // Проверки NewApi для java.time решаются desugaring'ом; выравниваем с ним.
        abortOnError = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Читаемое имя для файлов APK вместо некрасивого «app-…».
base {
    archivesName.set("twilight-calc")
}

// Убираем суффикс варианта («debug»/«release») из имени итогового APK.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("twilight-calc.apk")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    testImplementation("junit:junit:4.13.2")
}