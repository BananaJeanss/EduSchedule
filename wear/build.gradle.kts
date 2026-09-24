plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.bananajeans.eduschedule.wear"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.bananajeans.eduschedule"
        minSdk = 30
        targetSdk = 37
        versionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("versionName").orElse("0.4.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val signingFile = providers.environmentVariable("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        if (signingFile.isPresent) create("release") {
            storeFile = file(signingFile.get())
            storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingFile.isPresent) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.activity)
    implementation(libs.wear.compose.material3)
    implementation(libs.watchface.complications)
    implementation(project(":sync"))
    implementation(libs.wearable)
    implementation(libs.core)
    implementation(libs.coroutines)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    debugImplementation(libs.compose.test.manifest)
}
