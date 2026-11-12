plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "et.sera.nativeapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "et.sera.nativeapp"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = providers.gradleProperty("SERA_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.gradleProperty("SERA_VERSION_NAME").orElse("0.1.0").get()
        val supabaseUrl = providers.gradleProperty("SERA_SUPABASE_URL").orElse("").get()
        val supabaseAnonKey = providers.gradleProperty("SERA_SUPABASE_ANON_KEY").orElse("").get()
        val firebaseClient = file("google-services.json").readText()
        val firebaseConfig = groovy.json.JsonSlurper().parseText(firebaseClient) as Map<*, *>
        require((firebaseConfig["project_info"] as Map<*, *>)["project_id"] == "sera-8ecc2") { "SERA Firebase project mismatch" }
        require((firebaseConfig["client"] as List<*>).any { ((it as Map<*, *>)["client_info"] as Map<*, *>)["android_client_info"].let { info -> (info as Map<*, *>)["package_name"] == "et.sera.nativeapp" } }) { "SERA Android Firebase client missing" }
        val escapedFirebase = firebaseClient.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")
        buildConfigField("String", "FIREBASE_CLIENT_JSON", "\""+escapedFirebase+"\"")
        buildConfigField("String", "SUPABASE_URL", "\""+supabaseUrl+"\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\""+supabaseAnonKey+"\"")
    }
    // Smaller signed release APKs: R8 removes unused code/resources while debug
    // remains unminified for reproducible diagnostics.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    val releaseStore = providers.environmentVariable("SERA_RELEASE_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("SERA_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("SERA_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("SERA_RELEASE_KEY_PASSWORD").orNull
    if (listOf(releaseStore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }) {
        signingConfigs {
            create("seraRelease") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("seraRelease")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation("androidx.room:room-paging:2.7.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("io.github.webrtc-sdk:android:144.7559.15")
}
