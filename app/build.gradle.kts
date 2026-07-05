import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.github.triplet.play")
}

val releasePropertiesFile = rootProject.file("release.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) {
        releasePropertiesFile.inputStream().use(::load)
    }
}

fun releaseProperty(name: String): String? {
    return providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: releaseProperties.getProperty(name)?.takeIf { it.isNotBlank() }
}

val defaultPlayServiceAccountJson = "/Users/thuglifex/Desktop/satra-play-service-account.json"
val releaseSigningKeys = listOf(
    "SATRA_UPLOAD_STORE_FILE",
    "SATRA_UPLOAD_STORE_PASSWORD",
    "SATRA_UPLOAD_KEY_ALIAS",
    "SATRA_UPLOAD_KEY_PASSWORD",
)
val hasReleaseSigningConfig = releaseSigningKeys.all { releaseProperty(it) != null }

android {
    namespace = "dev.satra.wallet"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.satra.wallet"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseProperty("SATRA_UPLOAD_STORE_FILE")!!)
                storePassword = releaseProperty("SATRA_UPLOAD_STORE_PASSWORD")!!
                keyAlias = releaseProperty("SATRA_UPLOAD_KEY_ALIAS")!!
                keyPassword = releaseProperty("SATRA_UPLOAD_KEY_PASSWORD")!!
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

play {
    serviceAccountCredentials.set(file(releaseProperty("PLAY_SERVICE_ACCOUNT_JSON") ?: defaultPlayServiceAccountJson))
    track.set(releaseProperty("PLAY_TRACK") ?: "internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
    defaultToAppBundles.set(true)
}

val checkPlayReleaseConfig by tasks.registering {
    group = "publishing"
    description = "Checks local signing and Play upload credentials before building a release for Google Play."

    doLast {
        val missing = releaseSigningKeys
            .filter { releaseProperty(it) == null }
            .toMutableList()

        val credentialsFile = file(releaseProperty("PLAY_SERVICE_ACCOUNT_JSON") ?: defaultPlayServiceAccountJson)
        if (!credentialsFile.isFile) {
            missing += "PLAY_SERVICE_ACCOUNT_JSON (file not found: ${credentialsFile.absolutePath})"
        }

        val uploadStoreFile = releaseProperty("SATRA_UPLOAD_STORE_FILE")?.let(::file)
        if (uploadStoreFile != null && !uploadStoreFile.isFile) {
            missing += "SATRA_UPLOAD_STORE_FILE (file not found: ${uploadStoreFile.absolutePath})"
        }

        if (missing.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Missing Google Play release configuration:")
                    missing.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("Fill /Users/thuglifex/Documents/Android Bitcoin App/release.properties with the same upload key used for the first Play Console upload.")
                },
            )
        }
    }
}

tasks.matching {
    it.name in setOf(
        "bundleRelease",
        "publish",
        "publishBundle",
        "publishApk",
        "publishApps",
        "publishRelease",
        "publishReleaseBundle",
        "publishReleaseApk",
        "publishReleaseApps",
        "uploadReleasePrivateBundle",
        "uploadReleasePrivateApk",
        "installReleasePrivateArtifact",
    )
}.configureEach {
    dependsOn(checkPlayReleaseConfig)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.ton.kotlin:ton-kotlin-contract:0.5.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
