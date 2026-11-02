plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hy.wristnav"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hy.wristnav"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(files("libs/xms-wearable-lib_1.4_release.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    testImplementation("junit:junit:4.13.2")
    // Android's local JVM stubs do not implement JSONObject.
    testImplementation("org.json:json:20240303")
}
