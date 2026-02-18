import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinComposePlugin)
    alias(libs.plugins.jetbrainsCompose)
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
    compileOnly(compose.desktop.common)
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

val buildNativeMacOs by tasks.registering(Exec::class) {
    description = "Compiles the Objective-C JNI bridge into macOS dylibs (arm64 + x64)"
    group = "build"
    enabled = Os.isFamily(Os.FAMILY_MAC)

    val nativeDir = layout.projectDirectory.dir("src/main/native/macos")
    val outputDir = layout.projectDirectory.dir("src/main/resources/nucleus/native")
    inputs.dir(nativeDir)
    outputs.dir(outputDir)
    workingDir(nativeDir)
    commandLine("bash", "build.sh")
}

val buildNativeLinux by tasks.registering(Exec::class) {
    description = "Compiles the C JNI bridge into Linux shared libraries (x64 + aarch64)"
    group = "build"
    enabled = Os.isFamily(Os.FAMILY_UNIX) && !Os.isFamily(Os.FAMILY_MAC)

    val nativeDir = layout.projectDirectory.dir("src/main/native/linux")
    val outputDir = layout.projectDirectory.dir("src/main/resources/nucleus/native")
    inputs.dir(nativeDir)
    outputs.dir(outputDir)
    workingDir(nativeDir)
    commandLine("bash", "build.sh")
}

val buildNativeWindows by tasks.registering(Exec::class) {
    description = "Compiles the C JNI bridge into Windows DLLs (x64 + ARM64)"
    group = "build"
    enabled = Os.isFamily(Os.FAMILY_WINDOWS)

    val nativeDir = layout.projectDirectory.dir("src/main/native/windows")
    val outputDir = layout.projectDirectory.dir("src/main/resources/nucleus/native")
    inputs.dir(nativeDir)
    outputs.dir(outputDir)
    workingDir(nativeDir)
    commandLine("cmd", "/c", nativeDir.file("build.bat").asFile.absolutePath)
}

tasks.processResources {
    dependsOn(buildNativeMacOs, buildNativeWindows, buildNativeLinux)
}

tasks.configureEach {
    if (name == "sourcesJar") {
        dependsOn(buildNativeMacOs, buildNativeWindows, buildNativeLinux)
    }
}

mavenPublishing {
    coordinates("io.github.kdroidfilter", "nucleus.darkmode-detector", publishVersion)

    pom {
        name.set("Nucleus Dark Mode Detector")
        description.set("Reactive dark mode detection for Compose Desktop across Windows, macOS and Linux")
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
