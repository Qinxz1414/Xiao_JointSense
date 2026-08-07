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
    testImplementation(libs.androidx.room.testing)
}
