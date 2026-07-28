plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // Pinned to the JDK actually available in this environment (OpenJDK 21) rather than a
    // toolchain version that would require downloading a JDK we can't fetch here.
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
}
