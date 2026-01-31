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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
   /* kotlinOptions {
        jvmTarget = "11"
    }*/
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

kotlin {
    jvmToolchain(21)
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
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // PDF Viewer
    implementation(libs.android.pdf.viewer)

    // Epub Parser
    implementation("com.positiondev.epublib:epublib-core:3.1") {
        exclude(group = "org.xmlpull", module = "xmlpull")
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    // Navigation
    implementation(libs.androidx.navigation.compose)
    // Image Loading
    implementation(libs.coil.compose)
    // Material Icons
    implementation(libs.androidx.compose.material.icons.extended)

    val room = "2.8.4"
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    //ksp("androidx.room:room-compiler:$roomVersion")
    add("ksp", "androidx.room:room-compiler:$room")
}