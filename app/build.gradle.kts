plugins { id("com.android.application") }

android {
    namespace = "jp.crescendo.xtranslator"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.crescendo.xtranslator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.mlkit:translate:17.0.3")
}
