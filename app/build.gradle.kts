plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.nonlog.oplusfluidcompat"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.nonlog.oplusfluidcompat"
        minSdk = 35
        targetSdk = 36
        versionCode = 16
        versionName = "0.9.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    testImplementation("junit:junit:4.13.2")
}
