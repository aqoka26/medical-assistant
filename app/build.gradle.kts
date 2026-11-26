import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Proxy address: from the PROXY_URL build variable, or else from app/proxy.properties.
val proxyProps = Properties().apply {
    val f = file("proxy.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val proxyUrl = (System.getenv("PROXY_URL")?.takeIf { it.isNotBlank() } ?: proxyProps.getProperty("PROXY_URL", ""))
    .trim().trimEnd('/')
val appToken = System.getenv("APP_TOKEN")?.takeIf { it.isNotBlank() } ?: proxyProps.getProperty("APP_TOKEN", "")

android {
    namespace = "app.medassist"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.medassist"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Address of your Cloudflare proxy and its app token, passed in by the GitHub build.
        // When PROXY_URL is empty the app falls back to asking each user for their own key.
        buildConfigField("String", "PROXY_URL", "\"$proxyUrl\"")
        buildConfigField("String", "APP_TOKEN", "\"${appToken.trim()}\"")
    }

    signingConfigs {
        // One fixed key, so a newer APK installs over the old one and keeps your chats.
        create("shared") {
            storeFile = file("signing.keystore")
            storePassword = "medassist"
            keyAlias = "medassist"
            keyPassword = "medassist"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/9/module-info.class",
                "module-info.class",
            )
        }
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
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.30.0")
}
