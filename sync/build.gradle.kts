plugins { alias(libs.plugins.android.library) }
android {
    namespace = "dev.bananajeans.eduschedule.sync"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
