plugins {
    id("jointsense.android.library")
    id("jointsense.android.room")
}

android {
    namespace = "cloud.univ.jointsense.core.database"
}

dependencies {
    implementation(project(":core:domain"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
