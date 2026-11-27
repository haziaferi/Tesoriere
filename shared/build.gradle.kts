// :shared -- the engine: the money model, Sci-Fin's ported logic and Tesoriere's own (SPEC R04,
// R18), with no screens, and its storage (SPEC R141). Two targets, Android and desktop JVM, both
// JVMs -- which is what the jvmCommon source set below is for.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.hazia.tesoriere"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
    // expect/actual classes (Room's generated constructor of TesoriereDatabase) are Beta in Kotlin 2.x
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

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
            // The entities carry Room's annotations, so the stored shape is the engine's shape (SPEC R141).
            api(libs.androidx.room.runtime)
        }
        // Both apps reach the store through the bundled SQLite, which deletes nothing (SPEC R144):
        // Android's AndroidSQLiteDriver opens with the framework's default error handler, which
        // deletes a database it finds damaged. The guard's read-only probe uses the same library.
        jvmCommon.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        // The engine's tests run on the desktop JVM (SPEC R43): it is pure Kotlin, and the bundled
        // SQLite driver gives Room a real database without a device.
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

// SPEC R142: every schema version Room compiles is written to shared/schemas/ and committed, because
// a migration can only be written from a version whose JSON is on disk.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
// Both KSP tasks write the same file; with org.gradle.parallel they must not do it at once.
tasks.matching { it.name == "kspKotlinDesktop" }.configureEach {
    mustRunAfter("kspAndroidMain")
}

// Tests pass the same on any machine, whatever its language.
tasks.withType<Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}
