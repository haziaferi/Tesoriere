// :androidApp -- the Android host. No screen, activity, label or icon yet: every visible decision
// waits for the design pass (SPEC R13, R16). AGP 9 compiles Kotlin itself, so no Kotlin Android
// plugin is applied.
import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hazia.tesoriere"   // SPEC R03
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hazia.tesoriere"   // SPEC R03
        minSdk = 30   // SPEC R09
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(project(":shared"))
}

/**
 * SPEC R08: while the spec lists no network feature, the app must not be able to open a socket.
 * Checked on the MERGED manifest, so a library that declares the permission fails the build too.
 */
abstract class VerifyNoNetworkPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun verify() {
        if (manifest.get().asFile.readText().contains("android.permission.INTERNET")) {
            throw GradleException("android.permission.INTERNET is declared; SPEC R08 lists no network feature")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val check = tasks.register<VerifyNoNetworkPermission>(
            "verifyNoNetworkPermission" + variant.name.replaceFirstChar { it.uppercase() },
        ) {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.named("check") { dependsOn(check) }
    }
}
