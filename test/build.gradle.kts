plugins {
    alias(libs.plugins.android.application)
}

// Runtime identity stays outside the repository and is supplied only for local car tests.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

android {
    namespace = "com.shilapi.xcertplay.hotspottest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shilapi.xcertplay.hotspottest"
        minSdk = 17
        // Keep the test APK on the API 17 compatibility behavior requested for the vehicle.
        targetSdk = 17
        multiDexEnabled = true
        versionCode = 5
        versionName = "1.4-api17-hotspot-lan-test"
    }

    // Reuse the app's resources and shared Activity; this module has its own APK identity.
    sourceSets.getByName("main").apply {
        res.srcDir("../mobile/src/main/res")
        localAuthenticationAssets?.let { assets.srcDir(it) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation("androidx.multidex:multidex:2.0.1")
}

val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { directory ->
        listOf("identity.pk8", "certificate.p7b")
            .map { directory.resolve("offline-mfi/$it").canonicalFile }
            .toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}

val verifyHotspotTestAuthentication by tasks.registering {
    group = "verification"
    description = "Require explicit external authentication assets for the hotspot test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Set DIPLAY_AUTH_ASSETS_DIR to build a standalone hotspot test APK."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { credential ->
                credential.isFile && credential.length() > 0
            }
        }) { "Standalone CarPlay authentication files are missing or empty" }
    }
}
tasks.named("preBuild") {
    dependsOn(rejectBundledCredentials, verifyHotspotTestAuthentication)
}

tasks.register("assembleHotspotTestDebug") {
    group = "build"
    description = "Build the standalone phone-hotspot test APK with explicit authentication assets."
    dependsOn(verifyHotspotTestAuthentication, "assembleDebug")
}
