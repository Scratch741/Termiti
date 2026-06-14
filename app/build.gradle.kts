import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.termiti"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.termiti"
        minSdk = 26
        targetSdk = 36
        // Verzování: versionName = SemVer (MAJOR.MINOR.PATCH), hra je v beta fázi (0.x).
        // versionCode MUSÍ růst o 1 při každém vydaném buildu (požadavek Androidu pro update).
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val props = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    }

    signingConfigs {
        create("release") {
            storeFile     = file(props["KEYSTORE_PATH"] as? String ?: "")
            storePassword = props["KEYSTORE_PASSWORD"] as? String ?: ""
            keyAlias      = props["KEY_ALIAS"]         as? String ?: ""
            keyPassword   = props["KEY_PASSWORD"]      as? String ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val ver = output.versionName.orNull ?: "0.0.0"
            output.outputFileName.set("darkmage-$ver-${variant.buildType}.apk")
        }
    }
}

// ── Synchronizace karet ze serveru ───────────────────────────────────────────
// Před každým buildem vygeneruje app/src/main/assets/cards.json z server/game/cards.js.
// Vyžaduje Node.js v PATH. Pokud node není dostupný, build pokračuje s poslední
// verzí cards.json (committed v repozitáři).
val syncCards by tasks.registering {
    group = "build"
    description = "Generates cards.json from server/game/cards.js"
    val outFile = file("src/main/assets/cards.json")
    val scriptFile = rootProject.file("server/game/export_cards_json.js")
    outputs.file(outFile)
    inputs.file(scriptFile)
    inputs.file(rootProject.file("server/game/cards.js"))
    doLast {
        outFile.parentFile.mkdirs()
        try {
            val process = ProcessBuilder("node", scriptFile.absolutePath)
                .redirectErrorStream(false)
                .start()
            val output = process.inputStream.readBytes()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.warn("syncCards: node exited with $exitCode, using existing cards.json")
            } else {
                outFile.writeBytes(output)
                logger.lifecycle("syncCards: cards.json updated (${outFile.length()} bytes)")
            }
        } catch (e: Exception) {
            logger.warn("syncCards: node not available (${e.message}), using existing cards.json")
        }
    }
}
tasks.named("preBuild") { dependsOn(syncCards) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // WebSocket pro Online multiplayer
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
// Serializace profilu a questů (PlayerProfileManager, QuestManager)
    implementation(libs.kotlinx.serialization.json)
}