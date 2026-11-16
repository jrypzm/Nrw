plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// yrs. home: a launcher built for speed. Plain Android views, no extra libraries, everything shrunk.
android {
    namespace = "app.years.home"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.years.home"
        minSdk = 30
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            storeFile = file("years-release.jks")
            storePassword = "years-sideload"
            keyAlias = "years"
            keyPassword = "years-sideload"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += listOf("META-INF/**", "kotlin/**", "DebugProbesKt.bin") } }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("robolectric.graphicsMode", "NATIVE"); it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2") } } }
}

// tests only: none of this goes into the app
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
