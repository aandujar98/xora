// Expose the vendored Discord Social SDK AAR as a project artifact.
// AGP forbids `implementation(files("….aar"))` on an Android library because
// `bundle*Aar` cannot package nested AARs. A plain Gradle project that only
// publishes the file is the supported workaround, and Prefab / JNI still
// resolve from the AAR the same way.
val aar = rootProject.file("core/launcher/libs/discord_partner_sdk.aar")
configurations.maybeCreate("default")
if (aar.isFile) {
    artifacts.add("default", aar)
}
