plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "com.loopsai.chat"
    compileSdk = 35

    defaultConfig {
        // Android 5.0. This is the floor: Jetpack Compose (LoopsAIChatView) and the
        // current AndroidX baseline require API 21. All WebView/session APIs the SDK
        // uses are available at 21, so older host apps can integrate.
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.fragment.compose)

    testImplementation(libs.junit)
    // Real org.json on the unit-test classpath: the android.jar stub throws
    // "not mocked", so this shadows it and lets bridge/analytics logic be tested
    // on plain JVM (no Robolectric).
    testImplementation("org.json:json:20231013")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.loopsai"
                artifactId = "loopsai-chat"
                version = "1.0.0"

                pom {
                    name.set("LoopsAI Chat SDK")
                    description.set("Official Android SDK for LoopsAI — embed AI-powered chat into any Android app.")
                    url.set("https://github.com/loopsmartai/loopsai-sdk-android")
                    licenses {
                        license {
                            name.set("Proprietary")
                            url.set("https://github.com/loopsmartai/loopsai-sdk-android/blob/main/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("loopsmartai")
                            name.set("Loops AI")
                            email.set("dev@loopsai.com")
                        }
                    }
                    scm {
                        url.set("https://github.com/loopsmartai/loopsai-sdk-android")
                        connection.set("scm:git:git://github.com/loopsmartai/loopsai-sdk-android.git")
                    }
                }
            }
        }
    }
}
