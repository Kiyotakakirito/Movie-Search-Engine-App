plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.student.moviesearch"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.student.moviesearch"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "BASE_URL", "\"${providers.gradleProperty("movieServer").getOrElse("http://10.0.2.2:8000/")}\"")
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
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
