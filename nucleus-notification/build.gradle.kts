import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktechMavenPublish)
}

val publishVersion =
    providers
        .environmentVariable("GITHUB_REF")
        .orNull
        ?.removePrefix("refs/tags/v")
        ?: "1.0.0"

dependencies {
    compileOnly(project(":core-runtime"))
    implementation(libs.kotlinx.coroutines.core)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

val nativeResourceDir = layout.projectDirectory.dir("src/main/resources/nucleus/native")

val buildNativeMacOs by tasks.registering(Exec::class) {
    description = "Compiles the Objective-C JNI bridge into macOS dylibs (arm64 + x64)"
    group = "build"
    val hasPrebuilt =
        nativeResourceDir
            .dir("darwin-aarch64")
            .file("libnucleus_notification.dylib")
            .asFile
            .exists()
    enabled = Os.isFamily(Os.FAMILY_MAC) && !hasPrebuilt

    val nativeDir = layout.projectDirectory.dir("src/main/native/macos")
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceDir)
    workingDir(nativeDir)
    commandLine("bash", "build.sh")
}

val buildNativeLinux by tasks.registering(Exec::class) {
    description = "Compiles the C JNI bridge into Linux shared library (x64 + aarch64)"
    group = "build"
    val hasPrebuiltX64 = nativeResourceDir
        .dir("linux-x64")
        .file("libnucleus_notification.so")
        .asFile
        .exists()
    // Build if x64 is missing (local dev) or if CI is detected (for ARM64)
    val isCI = System.getenv("CI") != null
    enabled = (Os.isFamily(Os.FAMILY_UNIX) && !Os.isFamily(Os.FAMILY_MAC)) && (!hasPrebuiltX64 || isCI)

    val nativeDir = layout.projectDirectory.dir("src/main/native/linux")
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceDir)
    workingDir(nativeDir)
    commandLine("bash", "build.sh")
}

// Task to ensure ARM64 is built before publishing (CI only)
val buildNativeLinuxArm64ForPublish by tasks.registering(Exec::class) {
    description = "Cross-compiles ARM64 Linux native library for publishing"
    group = "publish"
    val isCI = System.getenv("CI") != null
    val hasArm64 = nativeResourceDir
        .dir("linux-aarch64")
        .file("libnucleus_notification.so")
        .asFile
        .exists()
    
    // Only run in CI when publishing and ARM64 not already built
    enabled = isCI && !hasArm64

    val nativeDir = layout.projectDirectory.dir("src/main/native/linux")
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceDir.dir("linux-aarch64"))
    workingDir(nativeDir)
    
    // Set CI flag to force ARM64 build
    environment("CI", "true")
    commandLine("bash", "build.sh")
}

tasks.processResources {
    dependsOn(buildNativeMacOs)
    dependsOn(buildNativeLinux)
}

tasks.configureEach {
    if (name == "sourcesJar") {
        dependsOn(buildNativeMacOs)
        dependsOn(buildNativeLinux)
    }
    // Ensure ARM64 is built before publishing
    if (name.startsWith("publish") || name.startsWith("publishTo")) {
        dependsOn(buildNativeLinuxArm64ForPublish)
    }
}

mavenPublishing {
    coordinates("io.github.kdroidfilter", "nucleus.notification", publishVersion)

    pom {
        name.set("Nucleus Notification")
        description.set("Desktop notifications for Compose Desktop across Windows, macOS and Linux")
        url.set("https://github.com/kdroidFilter/Nucleus")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("kdroidfilter")
                name.set("kdroidFilter")
                url.set("https://github.com/kdroidFilter")
            }
        }

        scm {
            url.set("https://github.com/kdroidFilter/Nucleus")
            connection.set("scm:git:git://github.com/kdroidFilter/Nucleus.git")
            developerConnection.set("scm:git:ssh://git@github.com/kdroidFilter/Nucleus.git")
        }
    }

    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }
}
