import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

// Supabase credentials come from local.properties, which is gitignored. This keeps the
// project URL and anon key out of a public repo without the app having to fetch config at
// runtime. Absent values generate empty strings, which is exactly how sync stays switched off.
val supabaseProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val generateSupabaseConfig by tasks.registering {
    val url = supabaseProperties.getProperty("supabase.url", "")
    val anonKey = supabaseProperties.getProperty("supabase.anonKey", "")
    val outputDir = layout.buildDirectory.dir("generated/supabase")

    // Declared as inputs so editing local.properties actually re-runs this task.
    inputs.property("url", url)
    inputs.property("anonKey", anonKey)
    outputs.dir(outputDir)

    doLast {
        val target = outputDir.get().asFile
            .resolve("com/kanishk/splits/data/remote")
            .apply { mkdirs() }
            .resolve("GeneratedConfig.kt")

        // Built by concatenation rather than a template, so there is no ambiguity about
        // which layer of string interpolation the values belong to.
        target.writeText(
            buildString {
                appendLine("package com.kanishk.splits.data.remote")
                appendLine()
                appendLine("// Generated from local.properties by :composeApp:generateSupabaseConfig.")
                appendLine("// Do not edit and do not commit - this lives under build/.")
                appendLine("internal object GeneratedConfig {")
                append("    const val SUPABASE_URL: String = \"")
                append(url)
                appendLine("\"")
                append("    const val SUPABASE_ANON_KEY: String = \"")
                append(anonKey)
                appendLine("\"")
                appendLine("}")
            }
        )
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Compose Multiplatform 1.11 no longer ships iosX64 (Intel simulator) artifacts,
    // so we target real devices plus the Apple Silicon simulator.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSupabaseConfig)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.sqldelight.driver.android)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidUnitTest.dependencies {
            implementation(libs.junit)
        }
    }
}

// Signing material lives in keystore.properties, which is gitignored. Without it the
// release build still works — it just falls back to unsigned output.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null &&
    rootProject.file(keystoreProperties.getProperty("storeFile", "")).exists()

android {
    namespace = "com.kanishk.splits"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kanishk.splits"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 8
        versionName = "1.2.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 is left off deliberately: the shared Compose/SQLDelight/Ktor stack needs
            // keep rules that have not been exercised here, and shipping a smaller build
            // that crashes is worse than shipping a larger one that works.
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

sqldelight {
    databases {
        create("SplitsDatabase") {
            packageName.set("com.kanishk.splits.db")
        }
    }
}
