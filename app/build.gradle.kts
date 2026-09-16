plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.resolvecommunity.awaydoomscrollin"
    compileSdk = 36

    val githubKeystoreFile = file("release.keystore")
    val githubStorePassword = System.getenv("KEYSTORE_PASSWORD")
    val githubKeyAlias = System.getenv("KEY_ALIAS")
    val githubKeyPassword = System.getenv("KEY_PASSWORD")
    val hasGithubSigning = githubKeystoreFile.exists() &&
        !githubStorePassword.isNullOrBlank() &&
        !githubKeyAlias.isNullOrBlank() &&
        !githubKeyPassword.isNullOrBlank()

    val playUploadKeystoreFile = file("play-upload.keystore")
    val playStorePassword = System.getenv("PLAY_UPLOAD_KEYSTORE_PASSWORD")
    val playKeyAlias = System.getenv("PLAY_UPLOAD_KEY_ALIAS")
    val playKeyPassword = System.getenv("PLAY_UPLOAD_KEY_PASSWORD")
    val hasPlayUploadSigning = playUploadKeystoreFile.exists() &&
        !playStorePassword.isNullOrBlank() &&
        !playKeyAlias.isNullOrBlank() &&
        !playKeyPassword.isNullOrBlank()

    defaultConfig {
        applicationId = "com.resolvecommunity.awaydoomscrollin"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.1"
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            if (hasPlayUploadSigning) {
                storeFile = playUploadKeystoreFile
                storePassword = playStorePassword
                keyAlias = playKeyAlias
                keyPassword = playKeyPassword
            } else if (hasGithubSigning) {
                storeFile = githubKeystoreFile
                storePassword = githubStorePassword
                keyAlias = githubKeyAlias
                keyPassword = githubKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Keep local device builds isolated from the release-signed app so
            // testing never requires uninstalling it or erasing user data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasPlayUploadSigning || hasGithubSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint {
        abortOnError = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    val verifyReleaseSigningInputs = tasks.register("verifyReleaseSigningInputs") {
        group = "verification"
        description = "Prevents release artifacts from being created without a configured signing key."
        doLast {
            check(hasPlayUploadSigning || hasGithubSigning) {
                "Release signing is not configured. Provide the Play upload key (preferred for AAB) " +
                    "or the GitHub release key and its matching environment variables."
            }
        }
    }
    tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }
        .configureEach {
            dependsOn(verifyReleaseSigningInputs)
        }
}

tasks.withType<Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}

dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
