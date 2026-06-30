plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

// The published version follows the git tag JitPack checks out (e.g. tag `v1.0.1`
// -> "1.0.1"), so the artifact's version always matches the requested coordinate.
// Falls back to a default for local/untagged builds.
val sdkVersion: String = run {
    try {
        val proc = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val tag = proc.inputStream.bufferedReader().use { it.readText() }.trim()
        proc.waitFor()
        tag.removePrefix("v").ifBlank { "1.0.0" }
    } catch (e: Exception) {
        "1.0.0"
    }
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
                version = sdkVersion

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
