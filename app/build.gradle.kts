import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
 * Local credentials are read from git-ignored properties files (or
 * environment variables) and injected through BuildConfig. They are
 * never hardcoded in source, so the keys stay out of version control.
 *
 *   lastfm.apiKey             -> BuildConfig.LASTFM_API_KEY
 *   lastfm.sharedSecret       -> only injected if lastfm.enableWriteAuth=true
 *   lastfm.enableWriteAuth    -> BuildConfig.LASTFM_WRITE_AUTH_ENABLED
 */
val localCredentials = Properties().apply {
    listOf("lastfm.properties").forEach { fileName ->
        val localFile = rootProject.file(fileName)
        if (localFile.exists()) {
            localFile.inputStream().use { load(it) }
        }
    }
}

fun localSetting(property: String, environment: String): String =
    localCredentials.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environment)?.takeIf { it.isNotBlank() }
        ?: ""

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val lastFmApiKey = localSetting("lastfm.apiKey", "LASTFM_API_KEY")
val lastFmSharedSecret = localSetting("lastfm.sharedSecret", "LASTFM_SHARED_SECRET")
val lastFmWriteAuthEnabled =
    localSetting("lastfm.enableWriteAuth", "LASTFM_ENABLE_WRITE_AUTH").toBoolean()

android {
    namespace = "com.dezire.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dezire.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "LASTFM_API_KEY", lastFmApiKey.asBuildConfigString())
        buildConfigField(
            "boolean",
            "LASTFM_WRITE_AUTH_ENABLED",
            lastFmWriteAuthEnabled.toString()
        )
        if (lastFmWriteAuthEnabled) {
            buildConfigField(
                "String",
                "LASTFM_SHARED_SECRET",
                lastFmSharedSecret.asBuildConfigString()
            )
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

