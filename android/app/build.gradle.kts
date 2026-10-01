plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "co.tuvida.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "co.tuvida.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.1"
        ndk {
            val supported = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            val selected = providers.gradleProperty("tuVidaAbi").orNull
            require(selected == null || selected in supported) { "Arquitectura Android no válida: $selected" }
            abiFilters += if (selected == null) supported else setOf(selected)
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        jniLibs.useLegacyPackaging = true
    }
}
dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("net.sf.biweekly:biweekly:0.6.8")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
