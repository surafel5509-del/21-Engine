plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingValues = listOf(
    "SENGINE_KEYSTORE_PATH", "SENGINE_KEYSTORE_PASSWORD",
    "SENGINE_KEY_ALIAS", "SENGINE_KEY_PASSWORD",
).map { System.getenv(it) }
val canSignRelease = signingValues.all { !it.isNullOrBlank() }

android {
    namespace = "com.sengine.player"
    compileSdk = 35

    defaultConfig {
        applicationId = providers.gradleProperty("gameApplicationId").orElse("com.sengine.game").get()
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("gameVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("gameVersionName").orElse("1.0").get()
        resValue("string", "game_name", providers.gradleProperty("gameName").orElse("S Engine Game").get())
        manifestPlaceholders["gameIcon"] = if (providers.gradleProperty("gameHasIcon").orElse("false").get() == "true")
            "@drawable/game_icon" else "@drawable/default_game_icon"
    }

    // Populated by tools/build_android_game.py; no generated project or icons enter Git.
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("stagedGame"))
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generatedIcons"))

    if (canSignRelease) {
        signingConfigs {
            create("gameRelease") {
                storeFile = file(signingValues[0]!!)
                storePassword = signingValues[1]
                keyAlias = signingValues[2]
                keyPassword = signingValues[3]
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("gameRelease")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// A direct :player:assembleDebug without staging must fail, not produce a misleading empty game.
tasks.matching { it.name == "preBuild" }.configureEach {
    doFirst {
        check(layout.buildDirectory.file("stagedGame/game/project.json").get().asFile.isFile) {
            "No game staged. Run python3 tools/build_android_game.py <your-project.sengine> instead of building :player directly."
        }
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":renderer"))
}
