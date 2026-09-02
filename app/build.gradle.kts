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

configurations.all {
    // ML Kit pulls in the old split kotlin-stdlib-jdk7/jdk8 artifacts, which now
    // duplicate classes already merged into kotlin-stdlib itself.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
}
