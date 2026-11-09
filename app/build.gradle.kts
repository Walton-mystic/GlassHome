plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "se.glasshome"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.glasshome"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            // The keystore is not in the repo: the build workflow writes it
            // from the repository secret SIGNING_KEY before building.
            storeFile = file("glasshome.jks")
            storePassword = System.getenv("SIGNING_PASSWORD") ?: "glasshome123"
            keyAlias = "glasshome"
            keyPassword = System.getenv("SIGNING_PASSWORD") ?: "glasshome123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
