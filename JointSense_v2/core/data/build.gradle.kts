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
}
