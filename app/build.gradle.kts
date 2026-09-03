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

    signingConfigs {
        getByName("debug") {
            // CIのたびにランダムなデバッグ鍵で署名されると、APKを更新するたびに
            // 署名が変わって暗黙的に「アンインストール→再インストール」扱いになり、
            // 通知アクセス権限や保存データがそのたびに失われてしまう。
            // リポジトリに固定のデバッグ鍵を同梱し、常に同じ署名でビルドすることで
            // 通常のアップデートとして扱われるようにする。
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module"
            )
        }
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment:1.8.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
}
