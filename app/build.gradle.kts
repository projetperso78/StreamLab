plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.cleanroom.streamlab"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.cleanroom.streamlab"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1-qa"
        testInstrumentationRunner = "app.cleanroom.streamlab.FrenchUiTestRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    // Upload key is read only from environment variables; nothing secret lives in the repository.
    // See docs/play/SIGNATURE_ET_PUBLICATION.md. Without these variables, release stays unsigned.
    val uploadStore = System.getenv("STREAMLAB_UPLOAD_STORE_FILE")?.takeIf { file(it).exists() }
    signingConfigs {
        if (uploadStore != null) create("upload") {
            storeFile = file(uploadStore)
            storePassword = System.getenv("STREAMLAB_UPLOAD_STORE_PASSWORD")
            keyAlias = System.getenv("STREAMLAB_UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("STREAMLAB_UPLOAD_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            if (uploadStore != null) signingConfig = signingConfigs.getByName("upload")
            // Smaller and harder to tamper with: unused code removed, the rest obfuscated.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Exactly the Play build, signed with the local debug key so it can be installed and
        // stress-tested before publishing. Never uploaded.
        create("releaseCheck") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("parkedDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".car"
            versionNameSuffix = "-car"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
    }
    buildFeatures { compose = true }
    lint { disable += "UnsafeIntentLaunch" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val media3 = "1.11.1"
    val room = "2.8.5"
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.12.3")
    // MainActivity is a FragmentActivity (Cast picker). Cast only pulls fragment 1.1.0 (2019), whose
    // 16-bit request-code check crashed every file picker: pin a current fragment.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.car.app:app:1.7.0")
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("androidx.room:room-testing:$room")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:5.1.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:5.1.0")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

