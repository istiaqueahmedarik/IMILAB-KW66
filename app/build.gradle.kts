plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.kw66.glass"
    compileSdk = 36
    defaultConfig { applicationId = "dev.kw66.glass"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    flavorDimensions += "intelligence"
    productFlavors {
        create("core") { dimension = "intelligence" }
        create("gemma") { dimension = "intelligence"; minSdk = 31; ndk { abiFilters += "arm64-v8a" } }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { implementation("androidx.core:core:1.17.0"); "gemmaImplementation"("com.google.ai.edge.litertlm:litertlm-android:0.18.1") }
