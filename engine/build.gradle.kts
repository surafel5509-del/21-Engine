plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Pure-Java Box2D port: no platform-specific native binaries, shared by editor and game APK.
    implementation("org.jbox2d:jbox2d-library:2.2.1.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
