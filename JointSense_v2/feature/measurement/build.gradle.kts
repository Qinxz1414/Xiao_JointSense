plugins {
    id("jointsense.android.library")
    id("jointsense.android.compose")
}

android {
    namespace = "cloud.univ.jointsense.feature.measurement"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:analysis"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
