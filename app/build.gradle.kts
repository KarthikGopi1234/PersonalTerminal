import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Optional local secrets (never committed – see .gitignore). Used for the Google Drive OAuth client id.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val googleWebClientId: String = (localProps.getProperty("GOOGLE_WEB_CLIENT_ID")
    ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
    ?: "")

// ---------------------------------------------------------------------------------------------
// Versioning – fully automatic, nothing to edit for a normal release.
//   app.version  (gradle.properties) = MAJOR.MINOR, bumped by hand only for feature milestones.
//   PATCH        = highest existing git tag `vMAJOR.MINOR.N` + 1 (0 when there is none). The release
//                  workflow creates that tag only after a *successful* build, so the version advances
//                  exactly once per published build; failed runs leave no gaps.
//   versionName  = MAJOR.MINOR.PATCH                e.g. 0.2.3   (local builds: 0.2.3-local)
//   versionCode  = GitHub Actions run number – strictly increasing, so every build installs as an
//                  upgrade over the previous one; 1 for local builds.
// CI reads the resolved values back from `printVersion` (app/build/version.properties) so the tag,
// release title, asset names and the APK can never disagree.
// ---------------------------------------------------------------------------------------------
val versionBase: String = providers.gradleProperty("app.version").getOrElse("0.1")
val existingVersionTags: String = runCatching {
    providers.exec {
        commandLine("git", "tag", "--list", "v$versionBase.*")
        isIgnoreExitValue = true
    }.standardOutput.asText.get()
}.getOrDefault("")
val versionTagPattern = Regex("""^v${Regex.escape(versionBase)}\.(\d+)$""")
val nextPatch: Int = existingVersionTags.lines()
    .mapNotNull { versionTagPattern.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
    .maxOrNull()?.plus(1) ?: 0
val ciBuildNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val appVersionName: String = "$versionBase.$nextPatch" + (if (ciBuildNumber == null) "-local" else "")
val appVersionCode: Int = ciBuildNumber ?: 1

// Release signing: provided through env vars / local.properties (CI decodes the keystore from secrets).
fun secret(name: String): String? = localProps.getProperty(name) ?: System.getenv(name)
val releaseStoreFile: String? = secret("RELEASE_STORE_FILE")
val hasReleaseKey: Boolean = !releaseStoreFile.isNullOrBlank() && file(releaseStoreFile).exists()

android {
    namespace = "dev.personalterminal"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.personalterminal"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Fall back to the debug key so the release APK is always installable; CI warns when this happens.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        // Version-bump suggestions are tracked manually; third-party jar internals are out of our control.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "TrustAllX509TrustManager")
        htmlReport = true
        textReport = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    testOptions {
        // Robolectric renders the real Compose screens on the JVM (used by the screenshot suite).
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "1536m" }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

/** Writes the resolved version to app/build/version.properties (consumed by the release workflow). */
tasks.register("printVersion") {
    val name = appVersionName
    val code = appVersionCode
    val out = layout.buildDirectory.file("version.properties")
    inputs.property("versionName", name)
    inputs.property("versionCode", code)
    outputs.file(out)
    doLast {
        out.get().asFile.apply { parentFile.mkdirs(); writeText("versionName=$name\nversionCode=$code\n") }
        println("versionName=$name versionCode=$code")
    }
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Home-screen widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Camera (watch photos)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)

    // Background work (auto backup)
    implementation(libs.androidx.work.runtime.ktx)

    // Google sign-in + Drive
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
    implementation(libs.google.http.client.gson) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.drive.api) {
        exclude(group = "org.apache.httpcomponents")
    }

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Tests (Robolectric + Compose test rule power the JVM screenshot suite; not shipped in the APK)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

/**
 * Regenerates the README screenshots (PNG files in `screenshots/`) from the real UI:
 *   ./gradlew screenshots
 * Renders every screen with Robolectric on the JVM – no emulator or device needed – against a
 * seeded demo profile, so the images are reproducible and always match the current code.
 * The suite lives in app/src/test/java/dev/personalterminal/screenshots and is excluded from the
 * normal unit-test run (it is documentation, not a test).
 */
val wantsScreenshots: Boolean = gradle.startParameter.taskNames.any { it.endsWith("screenshots") }
tasks.register("screenshots") {
    group = "documentation"
    description = "Renders the app screens into screenshots/ (Robolectric, no device needed)."
    dependsOn("testDebugUnitTest")
    val dir = rootProject.file("screenshots")
    doLast {
        // Composite banner for the top of the README: four key screens side by side.
        val parts = listOf("01-today", "04-timer", "05-watches", "08-profile").map { File(dir, "$it.png") }
        if (parts.all { it.exists() }) {
            val images = parts.map { ImageIO.read(it) }
            val gap = 24
            val fullW = images.sumOf { it.width } + gap * (images.size - 1)
            val fullH = images.maxOf { it.height }
            val scale = minOf(1.0, 2000.0 / fullW) // README-sized, keeps the repo lean
            val w = (fullW * scale).toInt()
            val h = (fullH * scale).toInt()
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            var x = 0.0
            images.forEach {
                g.drawImage(it, x.toInt(), 0, (it.width * scale).toInt(), (it.height * scale).toInt(), null)
                x += (it.width + gap) * scale
            }
            g.dispose()
            ImageIO.write(out, "png", File(dir, "hero.png"))
            println("screenshots: wrote screenshots/hero.png (${w}x${h})")
        }
    }
}
tasks.withType<Test>().configureEach {
    if (name != "testDebugUnitTest") return@configureEach
    if (wantsScreenshots) {
        val dir = rootProject.file("screenshots")
        systemProperty("screenshots.dir", dir.absolutePath)
        filter.includeTestsMatching("dev.personalterminal.screenshots.*")
        outputs.upToDateWhen { false }
        doFirst { dir.mkdirs() }
    } else {
        exclude("**/screenshots/**")
    }
}
