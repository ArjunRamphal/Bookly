plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    //alias(libs.plugins.ksp)
    //id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "com.example.bookly"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.bookly"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "Bookly.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.remote.creation.core)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // PDF Viewer
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.1")

    // Epub Parser
    implementation("com.positiondev.epublib:epublib-core:3.1") {
        exclude(group = "org.xmlpull", module = "xmlpull")
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Material Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.3")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    //ksp("androidx.room:room-compiler:$roomVersion")
    add("ksp", "androidx.room:room-compiler:$roomVersion")
}