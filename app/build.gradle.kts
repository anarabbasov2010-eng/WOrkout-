import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties()
val signingFile = rootProject.file("keystore.properties")
if (signingFile.exists()) signingFile.inputStream().use(signingProperties::load)
val releaseStoreFile = System.getenv("ANDROID_KEYSTORE_PATH") ?: signingProperties.getProperty("storeFile")

android {
    namespace = "com.workout.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.workout.app"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("versionName").orElse("0.1.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "RELEASE_SIGNER_SHA256", "\"${providers.gradleProperty("releaseSignerSha256").orElse("").get()}\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: signingProperties.getProperty("storePassword")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: signingProperties.getProperty("keyAlias")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.sceneview:sceneview:2.3.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")

    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")

    testImplementation("junit:junit:4.13.2")
}


// Bundle a pinned, openly licensed anatomical GLB as an app asset.
// Build-time integrity checks prevent a partial or altered model from entering the APK.
val anatomyAssetDirectory = layout.buildDirectory.dir("generated/anatomy-assets")
val anatomyModelFile = anatomyAssetDirectory.map { it.file("models/ecorche.glb") }
val anatomyModelUrl = "https://raw.githubusercontent.com/Charleeh/ecorche/320805e06aee51e2e6f6b22533eb2a59d43f2d4d/model/ecorche.glb"
val anatomyModelExpectedSize = 14_314_536L
val anatomyModelGitBlobSha1 = "4360e0abeed7892e2585f257857b6d2d040af85d"

fun calculateAnatomyGitBlobSha1(file: java.io.File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-1")
    digest.update(("blob " + file.length() + "\u0000").toByteArray(Charsets.UTF_8))
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadAnatomyModel by tasks.registering {
    inputs.property("sourceCommit", "320805e06aee51e2e6f6b22533eb2a59d43f2d4d")
    inputs.property("expectedGitBlobSha1", anatomyModelGitBlobSha1)
    outputs.file(anatomyModelFile)
    outputs.upToDateWhen {
        val file = anatomyModelFile.get().asFile
        file.isFile && file.length() == anatomyModelExpectedSize &&
            runCatching { calculateAnatomyGitBlobSha1(file) == anatomyModelGitBlobSha1 }.getOrDefault(false)
    }
    doLast {
        val destination = anatomyModelFile.get().asFile
        destination.parentFile.mkdirs()
        val connection = java.net.URL(anatomyModelUrl).openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            useCaches = false
        }
        connection.getInputStream().buffered().use { input ->
            destination.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        check(destination.length() == anatomyModelExpectedSize) {
            "Anatomy model download size mismatch; refusing to package an incomplete asset."
        }
        check(calculateAnatomyGitBlobSha1(destination) == anatomyModelGitBlobSha1) {
            "Anatomy model integrity check failed; refusing to package an unverified asset."
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(anatomyAssetDirectory)
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(downloadAnatomyModel)
    }
}
tasks.named("preBuild").configure { dependsOn(downloadAnatomyModel) }
