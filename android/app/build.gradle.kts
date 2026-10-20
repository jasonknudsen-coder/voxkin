plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "studio.voxkin.android"
 compileSdk = 36
 defaultConfig { applicationId = "studio.voxkin.android"; minSdk = 31; targetSdk = 36; versionCode = 14; versionName = "0.2.12"; testInstrumentationRunner = "studio.voxkin.android.IsolatedTestRunner" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.10.00"))
 implementation("androidx.activity:activity-compose:1.11.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("org.commonmark:commonmark:0.30.1")
 implementation("com.journeyapps:zxing-android-embedded:4.3.0")
 implementation("org.bouncycastle:bcprov-jdk18on:1.82")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test:core-ktx:1.7.0")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20250517")
}
