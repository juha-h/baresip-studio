// Fetches baresip's prebuilt native libraries into distribution/ before the
// native (CMake) build, so the app builds in Android Studio on any computer,
// Windows included. The libraries themselves are built by GitHub's
// "Native libraries" workflow (.github/workflows/native-libs.yml) and published
// as the release native-libs-<version>; the version is in native-libs.properties.
//
// If distribution/ already holds that version (or you built the libraries
// yourself and wrote the version into distribution/.native-libs-version),
// nothing is downloaded. See BUILDING.md.

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties
import java.util.zip.ZipInputStream

val nativeLibsVersion: String = Properties().run {
    rootProject.file("native-libs.properties").inputStream().use { load(it) }
    getProperty("version")?.trim() ?: throw GradleException("native-libs.properties has no version")
}
// -Pbusycall.nativeLibsUrl=... (or the same line in gradle.properties) points the
// download somewhere else, e.g. a copy on a local server.
val nativeLibsUrl: String = (findProperty("busycall.nativeLibsUrl") as String?)
    ?: "https://github.com/Ben-Showalter/BusyCall/releases/download/native-libs-$nativeLibsVersion/distribution.zip"
val distributionDir: File = rootProject.file("distribution")
val versionMarker = File(distributionDir, ".native-libs-version")
val downloadFile: File = layout.buildDirectory.file("native-libs/distribution-$nativeLibsVersion.zip").get().asFile

fun installedVersion(): String? = if (versionMarker.isFile) versionMarker.readText().trim() else null

val fetchNativeLibs by tasks.registering {
    group = "build setup"
    description = "Downloads baresip native libraries $nativeLibsVersion into distribution/ if needed."
    inputs.property("version", nativeLibsVersion)

    doLast {
        if (installedVersion() == nativeLibsVersion) return@doLast

        println("Downloading baresip native libraries $nativeLibsVersion from $nativeLibsUrl")
        downloadFile.parentFile.mkdirs()
        try {
            val connection = URI(nativeLibsUrl).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = true
            val status = connection.responseCode
            if (status != 200) throw GradleException("HTTP $status")
            connection.inputStream.use { input -> downloadFile.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            downloadFile.delete()
            throw GradleException(
                "Couldn't download the native libraries ($nativeLibsUrl): ${e.message}. " +
                    "On GitHub, open the repo's Actions tab and run the \"Native libraries\" workflow " +
                    "(it publishes release native-libs-$nativeLibsVersion), then build again. See BUILDING.md.",
                e
            )
        }

        // Replace whatever was in distribution/ (keep its .gitignore).
        distributionDir.mkdirs()
        distributionDir.listFiles()?.filter { it.name != ".gitignore" }?.forEach { it.deleteRecursively() }
        val root = distributionDir.canonicalFile
        ZipInputStream(downloadFile.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val target = File(root, entry.name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) {
                    throw GradleException("Unexpected path in distribution.zip: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        versionMarker.writeText(nativeLibsVersion)
        println("Native libraries $nativeLibsVersion installed in distribution/")
    }
}

// Before anything native is configured or built.
tasks.matching { task ->
    task.name == "preBuild" ||
        task.name.startsWith("configureCMake") ||
        task.name.startsWith("buildCMake") ||
        task.name.startsWith("externalNativeBuild")
}.configureEach { dependsOn(fetchNativeLibs) }
