// :desktopApp -- the Windows host (SPEC R06). No window yet: the main window and its packaging
// (Compose Desktop's application block) come with the design pass (SPEC R13, R16).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
}
