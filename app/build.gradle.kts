plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.efood_clone_2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.efood_clone_2"
        minSdk = 29
        targetSdk = 35
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    implementation(libs.flexbox)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}