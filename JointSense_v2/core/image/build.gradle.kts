plugins {
    id("jointsense.android.library")
}

android {
    namespace = "cloud.univ.jointsense.core.image"
}

dependencies {
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
