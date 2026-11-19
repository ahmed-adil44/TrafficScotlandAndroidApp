import java.util.Properties // To read files from Traffic Scotland

plugins {
    alias(libs.plugins.android.application)
}
//Creating two variables to read files
val trafficProperties = Properties()

val trafficFile = rootProject.file("traffic-scotland.properties")

if (trafficFile.exists()) {
    trafficFile.inputStream().use {
        trafficProperties.load(it)
    }
}

android {
    namespace = "com.example.abdullah_ahmed_s2530964"
    
    buildFeatures {
        buildConfig = true
    }

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.abdullah_ahmed_s2530964"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "TRAFFIC_SCOTLAND_CLIENT_ID",
            "\"${trafficProperties.getProperty("TRAFFIC_SCOTLAND_CLIENT_ID", "")}\""
        )

        buildConfigField(
            "String",
            "TRAFFIC_SCOTLAND_CLIENT_KEY",
            "\"${trafficProperties.getProperty("TRAFFIC_SCOTLAND_CLIENT_KEY", "")}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
