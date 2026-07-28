// Intentionally no plugins {} block here. :core and :app each apply their own plugins
// directly, with explicit versions from the version catalog, so nothing needs pre-declaring
// at the root. This matters for two reasons:
//  1. It keeps :core:test fully isolated from the Android Gradle Plugin, so it builds/tests
//     without the Android SDK (see gradle.properties' org.gradle.configureondemand=true).
//  2. Pre-declaring `kotlin-android apply false` here while AGP only resolved inside :app
//     caused a classloader-isolation failure ("Could not generate a decorated class for
//     type KotlinAndroidTarget > com/android/build/gradle/api/BaseVariant") — AGP and the
//     Kotlin Android plugin must resolve together in the same project scope, which now
//     happens entirely within :app's own plugins {} block.
