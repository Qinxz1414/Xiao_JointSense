plugins {
    id("jointsense.android.library")
    id("jointsense.android.compose")
}

android {
    namespace = "cloud.univ.jointsense.core.designsystem"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
