plugins {
    id("jointsense.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "cloud.univ.jointsense.core.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
