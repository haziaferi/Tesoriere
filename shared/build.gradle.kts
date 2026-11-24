// :shared -- the engine: the money model, Sci-Fin's ported logic and Tesoriere's own (SPEC R04,
// R18), with no screens. Two targets, Android and desktop JVM, both JVMs -- which is what the
// jvmCommon source set below is for.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.hazia.tesoriere"
version = "0.1.0"

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.hazia.tesoriere.shared"
        compileSdk = 37
        minSdk = 30   // SPEC R09
    }

    jvm("desktop")

    // A manual dependsOn edge (jvmCommon, below) switches the default hierarchy off unless this
    // is called first.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // Both targets are JVMs, so java.time, java.io and javax.crypto can be shared here
        // without being reachable from a non-JVM target.
        val jvmCommon = create("jvmCommon") { dependsOn(commonMain.get()) }
        androidMain { dependsOn(jvmCommon) }
        getByName("desktopMain") { dependsOn(jvmCommon) }

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // The entities carry Room's annotations now, so the stored shape is the engine's
            // shape; the database itself and its compiler come with storage (phase 1, Task 21).
            api(libs.androidx.room.runtime)
        }
        // The engine's tests run on the desktop JVM (SPEC R43): it is pure Kotlin.
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
        }
    }
}

// Tests pass the same on any machine, whatever its language.
tasks.withType<Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}
