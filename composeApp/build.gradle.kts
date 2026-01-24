import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget {
        // Room compiler'ın Android'de schema oluşturabilmesi için argüman
        // compilerOptions {
        //    jvmTarget.set(JvmTarget.JVM_11)
        // }
        // Not: compilerOptions DSL'i bazen sorun çıkarabilir, eski yöntem daha stabil:
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // SQLite KMP için native link ayarı
            linkerOpts.add("-lsqlite3")
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // Compose Core
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.icons.extended)

            // Lifecycle & Navigation
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)

            // Koin (DI)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Serialization & Permissions
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.moko.permissions)

            // --- ROOM & SQLITE (COMMON) ---
            // Room artık commonMain'de tanımlanıyor!
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)

            // Map Dependencies (Android Only)
            implementation(libs.google.maps.compose)
            implementation(libs.play.services.maps)
            implementation(libs.play.services.location)

            // Preview support
            implementation(libs.compose.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
    }
}

android {
    namespace = "com.vahitkeskin.bluenix"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vahitkeskin.bluenix"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// --- ROOM SCHEMA LOCATION ---
room {
    schemaDirectory("$projectDir/schemas")
}

// --- KSP CONFIGURATION FOR KMP ---
// Burası Room Compiler'ın "Common" kodları tarayıp Database kodunu üretmesini sağlar.
dependencies {
    // Android Debug için Tooling
    debugImplementation(libs.compose.uiTooling)

    // KMP Room Compiler Config
    // "kspCommonMainMetadata" değil, doğrudan proje bağımlılığı olarak ekliyoruz
    // Ancak KMP'de 'add' ile source set hedeflemek şu an en güvenli yoldur.
    add("kspCommonMainMetadata", libs.androidx.room.compiler)

    // Garanti olması için Android target'ına da ekleyelim
    add("kspAndroid", libs.androidx.room.compiler)

    // iOS için (Eğer native derlemede sorun yaşarsan burayı açabilirsin)
    // add("kspIosArm64", libs.androidx.room.compiler)
    // add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

compose.desktop {
    application {
        mainClass = "com.vahitkeskin.bluenix.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.vahitkeskin.bluenix"
            packageVersion = "1.0.0"
        }
    }
}