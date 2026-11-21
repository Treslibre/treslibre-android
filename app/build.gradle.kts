// Modified 2026 by Treslibre Project (Treslibre).
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.gradle.internal.tasks.factory.dependsOn
import config.BuildFeatureFlags
import config.PublicKeys
import config.SentryConfig
import config.buildFlavorsWithoutRelease
import config.configureFlavor
import config.setProductNames
import config.setSentryConfig
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.lintChecks
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import utils.*

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.rust.android)
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.stem)
    alias(libs.plugins.koin.compiler)
}

/**
 * Only use the scheme "<major>.<minor>.<patch>" for the appVersion
 */
val appVersion = "6.6.1"

/**
 * betaSuffix with leading dash (e.g. `-beta1`).
 * Should be one of (alpha|beta|rc) and an increasing number, or empty for a regular release.
 * Note: in nightly builds this will be overwritten with a nightly version "-n12345"
 */
val betaSuffix = ""

val defaultVersionCode = 1240

/**
 * Map with keystore paths (if found).
 */
val keystores: Map<String, KeystoreConfig?> = mapOf(
    "debug" to findKeystore(projectDir, "debug"),
    "release" to findKeystore(projectDir, "treslibre"),
)

val requestedCargoTargets = (findProperty("cargoTargets") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: listOf("arm64", "armeabi-v7a", "x86", "x86_64").let {
        listOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
    }

val cargoTargetList = (findProperty("cargoTargets") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: listOf("x86_64", "arm64", "arm", "x86")

val enabledAbiSplits = cargoTargetList.mapNotNull { target ->
    when (target) {
        "arm64" -> "arm64-v8a"
        "arm" -> "armeabi-v7a"
        "x86" -> "x86"
        "x86_64" -> "x86_64"
        else -> null
    }
}.toTypedArray()

android {
    // NOTE: When adjusting compileSdk, buildToolsVersion or ndkVersion, make sure to also adjust
    //   `scripts/Dockerfile`, `commonAndroid/build.gradle.kts`, and the image in `.gitlab-ci.yml`
    compileSdk = 37
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        // https://developer.android.com/training/testing/espresso/setup#analytics
        with(testInstrumentationRunnerArguments) {
            put("notAnnotation", "ch.threema.app.DangerousTest")
            put("disableAnalytics", "true")
        }
        minSdk = 24
        targetSdk = 36
        vectorDrawables.useSupportLibrary = true
        applicationId = "org.treslibre.app"
        testApplicationId = "$applicationId.test"
        versionCode = defaultVersionCode
        versionName = "$appVersion$betaSuffix"

        setProductNames(
            appName = "Treslibre",
            shortAppName = "Treslibre",
            companyName = "Treslibre Project",
            appNameDesktop = "Treslibre",
        )

        stringBuildConfigField("BASE_VERSION_NAME", appVersion)
        intBuildConfigField("DEFAULT_VERSION_CODE", defaultVersionCode)
        stringBuildConfigField("VERSION_NAME_BETA_SUFFIX", betaSuffix)

        // package name used for sync adapter - needs to match mime types below
        stringResValue("package_name", applicationId!!)
        stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.$applicationId.profile")
        stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.$applicationId.call")

        intBuildConfigField("MAX_GROUP_SIZE", 256)
        stringBuildConfigField("CHAT_SERVER_PREFIX", "")
        stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "")
        stringBuildConfigField("CHAT_SERVER_SUFFIX", null)
        intArrayBuildConfigField("CHAT_SERVER_PORTS", intArrayOf(5222, 443))
        stringBuildConfigField("MEDIA_PATH", "Treslibre")
        booleanBuildConfigField("CHAT_SERVER_GROUPS", false)
        booleanBuildConfigField("DISABLE_CERT_PINNING", false)
        booleanBuildConfigField("VIDEO_CALLS_ENABLED", true)
        byteArrayBuildConfigField("SERVER_PUBKEY", null)
        byteArrayBuildConfigField("SERVER_PUBKEY_ALT", null)
        stringBuildConfigField("GIT_HASH", getGitHash())
        stringBuildConfigField("GIT_BRANCH", getGitBranch())
        stringBuildConfigField("DIRECTORY_SERVER_URL", null)
        stringBuildConfigField("DIRECTORY_SERVER_IPV6_URL", null)
        stringBuildConfigField("WORK_SERVER_URL_LEGACY", null)
        stringBuildConfigField("WORK_SERVER_IPV6_URL_LEGACY", null)
        stringBuildConfigField("WORK_SERVER_URL", null)
        stringBuildConfigField("MEDIATOR_SERVER_URL", null)

        // Base blob url used for "download" and "done" calls
        stringBuildConfigField("BLOB_SERVER_URL", null)
        stringBuildConfigField("BLOB_SERVER_IPV6_URL", null)

        // Specific blob url used for "upload" calls
        stringBuildConfigField("BLOB_SERVER_URL_UPLOAD", null)
        stringBuildConfigField("BLOB_SERVER_IPV6_URL_UPLOAD", null)

        // Base blob mirror url used for "download", "upload", "done"
        stringBuildConfigField("BLOB_MIRROR_SERVER_URL", null)

        stringBuildConfigField("AVATAR_FETCH_URL", null)
        stringBuildConfigField("SAFE_SERVER_URL", null)
        stringBuildConfigField("WEB_SERVER_URL", null)
        stringBuildConfigField("APP_RATING_URL", null)
        stringBuildConfigField("MAP_STYLES_URL", null)
        stringBuildConfigField("MAP_POI_AROUND_URL", null)
        stringBuildConfigField("MAP_POI_NAMES_URL", null)
        byteArrayBuildConfigField("THREEMA_PUSH_PUBLIC_KEY", null)
        stringBuildConfigField("ONPREM_ID_PREFIX", "O")
        stringBuildConfigField("LOG_TAG", "treslibre")

        stringArrayBuildConfigField("ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS", emptyArray())
        stringBuildConfigField("ONPREM_APP_IDENTIFIER", null)
        booleanBuildConfigField("MD_SYNC_DISTRIBUTION_LISTS", false)
        booleanBuildConfigField("AVAILABILITY_STATUS_ENABLED", false)
        booleanBuildConfigField("CROSS_PLATFORM_BACKUPS_ENABLED", BuildFeatureFlags["cross_platform_backups"] ?: false)
        booleanBuildConfigField("NEW_CONVERSATION_SCREEN_ENABLED", BuildFeatureFlags["new_conversation_screen"] ?: false)
        booleanBuildConfigField("LOG_TOMBSTONES_FOR_NATIVE_CRASHES", BuildFeatureFlags["log_tombstones"] ?: false)

        // config fields for action URLs / deep links
        stringBuildConfigField("uriScheme", "treslibre")
        stringBuildConfigField("actionUrl", "")
        stringBuildConfigField("contactActionUrl", "")

        // The OPPF url must be null in the default config. Do not change this.
        stringBuildConfigField("PRESET_OPPF_URL", null)

        with(manifestPlaceholders) {
            put("uriScheme", "treslibre")
            put("contactActionUrl", "treslibre.invalid")
            put("actionUrl", "treslibre.invalid")
            put("callMimeType", "vnd.android.cursor.item/vnd.$applicationId.call")
        }

        testInstrumentationRunner = "ch.threema.app.ThreemaTestRunner"

        // Only include language resources for those languages
        androidResources.localeFilters.addAll(
            setOf(
                "en",
                "be-rBY",
                "bg",
                "ca",
                "cs",
                "de",
                "es",
                "fr",
                "gsw",
                "hu",
                "it",
                "ja",
                "nl-rNL",
                "no",
                "pl",
                "pt-rBR",
                "ru",
                "sk",
                "tr",
                "uk",
                "zh-rCN",
                "zh-rTW",
            ),
        )
    }

    splits {
        abi {
            isEnable = true
            reset()
            if (project.hasProperty("noAbiSplits")) {
                isUniversalApk = true
            } else {
                include(*enabledAbiSplits)
                isUniversalApk = project.hasProperty("buildUniversalApk")
            }
        }
    }

    // Assign different version code for each output
    android.applicationVariants.all {
        outputs.all {
            if (this is ApkVariantOutputImpl) {
                val abi = getFilter("ABI")
                val abiVersionCode = when (abi) {
                    "armeabi-v7a" -> 2
                    "arm64-v8a" -> 3
                    "x86" -> 8
                    "x86_64" -> 9
                    else -> 0
                }
                versionCodeOverride = abiVersionCode * 1_000_000 + defaultVersionCode
            }
        }
    }

    namespace = "ch.threema.app"
    flavorDimensions.add("default")
    productFlavors {
        create("treslibre") {
            versionName = "${appVersion}t$betaSuffix"
            applicationId = "org.treslibre.app"
            testApplicationId = "$applicationId.test"
            setProductNames(
                appName = "Treslibre",
                shortAppName = "Treslibre",
                companyName = "Treslibre Project",
                appNameDesktop = "Treslibre",
            )
            stringResValue("package_name", applicationId!!)
            stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.$applicationId.profile")
            stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.$applicationId.call")
            intBuildConfigField("MAX_GROUP_SIZE", 256)
            stringBuildConfigField("CHAT_SERVER_PREFIX", "")
            stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "")
            stringBuildConfigField("CHAT_SERVER_SUFFIX", null)
            stringBuildConfigField("MEDIA_PATH", "Treslibre")
            booleanBuildConfigField("CHAT_SERVER_GROUPS", false)
            byteArrayBuildConfigField("SERVER_PUBKEY", null)
            byteArrayBuildConfigField("SERVER_PUBKEY_ALT", null)
            stringBuildConfigField("DIRECTORY_SERVER_URL", null)
            stringBuildConfigField("DIRECTORY_SERVER_IPV6_URL", null)
            stringBuildConfigField("BLOB_SERVER_URL", null)
            stringBuildConfigField("BLOB_SERVER_IPV6_URL", null)
            stringBuildConfigField("BLOB_SERVER_URL_UPLOAD", null)
            stringBuildConfigField("BLOB_SERVER_IPV6_URL_UPLOAD", null)
            stringBuildConfigField("BLOB_MIRROR_SERVER_URL", null)
            stringArrayBuildConfigField("ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS", emptyArray())
            stringBuildConfigField("ONPREM_APP_IDENTIFIER", null)
            stringBuildConfigField("LOG_TAG", "treslibre")

            val uriScheme = "treslibre"
            val actionUrl = "treslibre.invalid"
            stringBuildConfigField("uriScheme", uriScheme)
            stringBuildConfigField("actionUrl", actionUrl)

            stringBuildConfigField("PRESET_OPPF_URL", null)
            stringBuildConfigField("MD_CLIENT_DOWNLOAD_URL", "https://github.com/Treslibre/treslibre-desktop")

            stringBuildConfigField("SCREENSHOT_TEST_ONPREM_USERNAME", LocalProperties.getString("screenshotTestOnPremUsername"))
            stringBuildConfigField("SCREENSHOT_TEST_ONPREM_PASSWORD", LocalProperties.getString("screenshotTestOnPremPassword"))
            stringBuildConfigField("SCREENSHOT_TEST_ONPREM_SERVER_URL", LocalProperties.getString("screenshotTestOnPremServerUrl"))

            with(manifestPlaceholders) {
                put("uriScheme", uriScheme)
                put("actionUrl", actionUrl)
                put("callMimeType", "vnd.android.cursor.item/vnd.$applicationId.call")
            }

            setSentryConfig(null)
            proguardFile("proguard-production.txt")
        }
    }

    signingConfigs {
        // Debug config
        keystores["debug"]
            ?.let { keystore ->
                getByName("debug") {
                    storeFile = keystore.storeFile
                }
            }
            ?: run {
                logger.warn("No debug keystore found. Falling back to locally generated keystore.")
            }

        // Release config
        keystores["release"]
            ?.let { keystore ->
                create("release") {
                    apply(keystore)
                }
            }
            ?: run {
                logger.warn("No release keystore found. Falling back to locally generated keystore.")
            }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
            jniLibs.srcDirs("libs")
            res.srcDir("src/main/res-rendezvous")
            java.srcDir("./build/generated/source/protobuf/main/java")
            java.srcDir("./build/generated/source/protobuf/main/kotlin")
        }

        configureFlavor("treslibre") {
            threemaOnPrem()
            withFossBasedServices()
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false

            if (keystores["debug"] != null) {
                signingConfig = signingConfigs["debug"]
            }
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false // Caused inconsistencies between local and CI builds
            vcsInfo.include = false // For reproducible builds independent from git history
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-project.txt")
            ndk {
                debugSymbolLevel = "FULL" // 'SYMBOL_TABLE'
            }

            if (keystores["release"] != null) {
                productFlavors["treslibre"].signingConfig = signingConfigs["release"]
            }
        }
    }

    packaging {
        jniLibs {
            // replacement for extractNativeLibs in AndroidManifest
            useLegacyPackaging = true
        }
        resources {
            excludes.addAll(
                setOf(
                    "META-INF/DEPENDENCIES.txt",
                    "META-INF/LICENSE.txt",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md",
                    "META-INF/NOTICE.txt",
                    "META-INF/NOTICE",
                    "META-INF/LICENSE",
                    "META-INF/DEPENDENCIES",
                    "META-INF/notice.txt",
                    "META-INF/license.txt",
                    "META-INF/dependencies.txt",
                    "META-INF/LGPL2.1",
                    "**/*.proto",
                    "DebugProbesKt.bin",
                ),
            )
        }
    }

    testOptions {
        // Disable animations in instrumentation tests
        animationsDisabled = true

        unitTests {
            all { test ->
                test.outputs.upToDateWhen { false }
                test.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    exceptionFormat = TestExceptionFormat.FULL
                }

                test.jvmArgs = test.jvmArgs!! + listOf(
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.stream=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "-Xmx4096m",
                )
            }
            // By default, local unit tests throw an exception any time the code you are testing tries to access
            // Android platform APIs (unless you mock Android dependencies yourself or with a testing
            // framework like Mockk). However, you can enable the following property so that the test
            // returns either null or zero when accessing platform APIs, rather than throwing an exception.
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    kotlin {
        jvmToolchain(21)
    }

    androidResources {
        noCompress.add("png")
    }

    lint {
        // if true, stop the gradle build if errors are found
        abortOnError = true
        // if true, check all issues, including those that are off by default
        checkAllWarnings = true
        // check dependencies
        checkDependencies = true
        // set to true to have all release builds run lint on issues with severity=fatal
        // and abort the build (controlled by abortOnError above) if fatal issues are found
        checkReleaseBuilds = true
        // turn off checking the given issue id's
        disable.addAll(setOf("TypographyFractions", "TypographyQuotes", "RtlHardcoded", "RtlCompat", "RtlEnabled"))
        // Set the severity of the given issues to error
        error.addAll(setOf("Wakelock", "TextViewEdits", "ResourceAsColor"))
        // Set the severity of the given issues to fatal (which means they will be
        // checked during release builds (even if the lint target is not included)
        fatal.addAll(setOf("NewApi", "InlinedApi", "LoggerName"))
        ignoreWarnings = false
        // if true, don't include source code lines in the error output
        noLines = false
        // if true, show all locations for an error, do not truncate lists, etc.
        showAll = true
        // Set the severity of the given issues to warning
        warning.add("MissingTranslation")
        // if true, treat all warnings as errors
        warningsAsErrors = false
        // file to write report to (if not specified, defaults to lint-results.xml)
        xmlOutput = file("lint-report.xml")
        // if true, generate an XML report for use by for example Jenkins
        xmlReport = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

composeCompiler {
    includeSourceInformation = true
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

// Only build relevant buildType / flavor combinations
androidComponents {
    beforeVariants { variant ->
        val name = variant.name
        if (variant.buildType == "release" && buildFlavorsWithoutRelease.any { it in name }) {
            variant.enable = false
        }
    }
}

dependencies {
    configurations.all {
        // Prefer modules that are part of this build (multi-project or composite build)
        // over external modules
        resolutionStrategy.preferProjectModules()

        // Alternatively, we can fail eagerly on version conflict to see the conflicts
        // resolutionStrategy.failOnVersionConflict()
    }

    coreLibraryDesugaring(libs.desugarJdkLibs)

    implementation(project(":domain"))
    implementation(project("::commonAndroid"))
    implementation(project(":common"))
    lintChecks(project(":lint-rules"))

    // Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidCompat)
    implementation(libs.koin.annotations)
    implementation(libs.koin.compose)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    // Database
    implementation(libs.sqlcipher.android)

    // AndroidX / Jetpack support libraries
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.commonJava8)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.sharetarget)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.window)
    implementation(libs.androidx.splashscreen)
    ksp(libs.androidx.room.compiler)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))

    // UI / Views
    implementation(libs.subsamplingScaleImageView)
    implementation(libs.taptargetview)
    implementation(libs.fastscroll)
    implementation(libs.gestureViews)
    implementation(libs.material)
    implementation(libs.androidImageCropper)
    implementation(libs.zxing)

    // Cryptography
    implementation(libs.bcprov.jdk15to18)

    // Files / data processing
    implementation(libs.opencsv)
    implementation(libs.zip4j)
    implementation(libs.slf4j.api)
    implementation(libs.ezVcard)
    implementation(libs.libphonenumber)

    // webclient dependencies
    implementation(libs.msgpack.core)
    implementation(libs.jackson.core)
    // TODO(ANDR-4869): remove `libs.jackson.databind` once we are on Gradle 9.7.0+ (workaround for https://github.com/gradle/gradle/issues/14220)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.msgpack)
    implementation(libs.nvWebsocket.client)

    implementation(libs.saltyrtc.client) {
        exclude(group = "org.json")
        exclude(module = "msgpack-core")
        exclude(module = "jackson-databind")
        exclude(module = "nv-websocket-client")
        exclude(module = "jackson-dataformat-msgpack")
        exclude(module = "chunked-dc")
    }

    implementation(libs.chunkedDc)
    implementation(libs.webrtcAndroid)
    implementation(libs.saltyrtc.taskWebrtc) {
        exclude(module = "saltyrtc-client")
        exclude(module = "msgpack-core")
        exclude(module = "jackson-databind")
        exclude(module = "jackson-dataformat-msgpack")
        exclude(module = "chunked-dc")
    }

    // Glide components
    implementation(libs.glide)
    ksp(libs.glide.ksp)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlin.test)

    // use leak canary in debug builds if requested
    if (project.hasProperty("leakCanary")) {
        debugImplementation(libs.leakcanary)
    }

    // test dependencies
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":domain")))

    // custom test helpers, shared between unit test and android tests
    testImplementation(project(":test-helpers"))
    androidTestImplementation(project(":test-helpers"))

    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockkAndroid)

    // add JSON support to tests without mocking
    testImplementation(libs.json)

    testImplementation(libs.archunit.junit4)

    androidTestImplementation(testFixtures(project(":domain")))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core) {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    androidTestImplementation(libs.androidx.test.runner) {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        exclude(group = "androidx.annotation", module = "annotation")
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "androidx.legacy", module = "legacy-support-v4")
        exclude(group = "com.google.android.material", module = "material")
        exclude(group = "androidx.recyclerview", module = "recyclerview")
        exclude(group = "org.checkerframework", module = "checker")
        exclude(module = "protobuf-lite")
    }
    androidTestImplementation(libs.androidx.espresso.intents) {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    // Maplibre (exclude Google location services for FOSS-based Treslibre build)
    "treslibreImplementation"(libs.maplibre) {
        exclude(group = "com.google.android.gms")
    }
}

// Define the cargo attributes. These will be used by the rust-android plugin that will create the
// 'cargoBuild' task that builds native libraries that will be added to the apk. Note that the
// kotlin bindings are created in the domain module. Building native libraries with rust-android
// cannot be done in any other module than 'app'.
cargo {
    targetDirectory = "$projectDir/build/generated/source/libthreema"
    module = "$projectDir/../domain/libthreema" // must contain Cargo.toml
    libname = "libthreema" // must match the Cargo.toml's package name
    profile = "release"
    pythonCommand = if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3"
    targets = cargoTargetList
    features {
        defaultAnd(arrayOf("uniffi"))
    }
    extraCargoBuildArguments = listOf("--lib", "--target-dir", "$projectDir/build/generated/source/libthreema", "--locked")
    verbose = false
}

afterEvaluate {
    // The `cargoBuild` task isn't available until after evaluation.
    android.applicationVariants.configureEach {
        val variantName = name.replaceFirstChar { it.uppercase() }
        // Set the dependency so that cargoBuild is executed before the native libs are merged
        tasks["merge${variantName}NativeLibs"].dependsOn(tasks["cargoBuild"])
    }
}

androidStem {
    includeLocalizedOnlyTemplates = true
}

tasks.register<Exec>("compileProto") {
    group = "build"
    description = "generate class bindings from protobuf files in the 'protobuf' directory"
    workingDir(project.projectDir)
    if (System.getProperty("os.name").lowercase().contains("win")) {
        commandLine("C:\\Program Files\\Git\\bin\\bash.exe", "./compile-proto.sh")
    } else {
        commandLine("./compile-proto.sh")
    }
}
project.tasks.preBuild.dependsOn("compileProto")

tasks.withType<Test> {
    // Necessary to load the dynamic libthreema library in unit tests
    systemProperty("jna.library.path", "${project.projectDir}/../domain/libthreema/target/release")
}

// Set up Gradle tasks to fetch screenshots on UI test failures
// See https://medium.com/stepstone-tech/how-to-capture-screenshots-for-failed-ui-tests-9927eea6e1e4
val reportsDirectory = "${layout.buildDirectory}/reports/androidTests/connected"
val screenshotsDirectory = "/sdcard/testfailures/screenshots/"
val clearScreenshotsTask = tasks.register<Exec>("clearScreenshots") {
    executable = android.adbExecutable.toString()
    args("shell", "rm", "-r", screenshotsDirectory)
}
val createScreenshotsDirectoryTask = tasks.register<Exec>("createScreenshotsDirectory") {
    group = "reporting"
    executable = android.adbExecutable.toString()
    args("shell", "mkdir", "-p", screenshotsDirectory)
}
val fetchScreenshotsTask = tasks.register<Exec>("fetchScreenshots") {
    group = "reporting"
    executable = android.adbExecutable.toString()
    args("pull", "$screenshotsDirectory.", reportsDirectory)
    finalizedBy(clearScreenshotsTask)
    dependsOn(createScreenshotsDirectoryTask)
    doFirst {
        file(reportsDirectory).mkdirs()
    }
}
tasks.whenTaskAdded {
    if (name == "connectedDebugAndroidTest") {
        finalizedBy(fetchScreenshotsTask)
    }
}

// Let the compose compiler generate stability reports
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        val composeCompilerReportsPath = "${project.layout.buildDirectory.get().dir("compose_compiler").asFile.absolutePath}/reports"
        freeCompilerArgs.addAll(
            listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$composeCompilerReportsPath",
            ),
        )
    }
}
