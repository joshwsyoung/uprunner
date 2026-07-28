// AGP is intentionally NOT declared here — it is applied only inside app/build.gradle.kts.
// This keeps `:core` fully buildable/testable without the Android SDK: with
// org.gradle.configureondemand=true (see gradle.properties), a task-scoped invocation like
// `./gradlew :core:test` never configures `:app`, so it never resolves the Android Gradle
// Plugin or touches dl.google.com.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
