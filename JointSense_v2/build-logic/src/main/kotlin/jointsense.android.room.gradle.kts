import com.google.devtools.ksp.gradle.KspExtension

plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    "implementation"("androidx.room:room-runtime:2.8.4")
    "implementation"("androidx.room:room-ktx:2.8.4")
    "ksp"("androidx.room:room-compiler:2.8.4")
}

extensions.configure<KspExtension> {
    arg("room.schemaLocation", "$projectDir/schemas")
}
