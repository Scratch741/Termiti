plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
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
            val result = exec {
                commandLine("node", scriptFile.absolutePath)
                standardOutput = outFile.outputStream()
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                logger.warn("syncCards: node exited with ${result.exitValue}, using existing cards.json")
            } else {
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
}