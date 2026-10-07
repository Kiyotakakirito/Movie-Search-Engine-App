import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val localSettings = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val tmdbToken = localSettings.getProperty("tmdb.token", "")
require(tmdbToken.matches(Regex("[A-Za-z0-9._-]*"))) { "Invalid TMDB token format in local.properties" }
android {
    namespace = "com.student.moviesearch"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.student.moviesearch"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "2.0"
        buildConfigField("String", "TMDB_TOKEN", "\"$tmdbToken\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
