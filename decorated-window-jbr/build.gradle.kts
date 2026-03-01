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
    api(project(":decorated-window-core"))
    compileOnly(project(":core-runtime"))
    compileOnly(compose.desktop.common)
    compileOnly(libs.jbr.api)
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

val nativeResourceFile = file("src/main/resources/nucleus/native")

val buildNativeMacOs by tasks.registering(Exec::class) {
    description = "Compiles the Objective-C JNI bridge into macOS dylibs (arm64 + x64)"
    group = "build"
    val nativeDir = file("src/main/native/macos")
    onlyIf {
        Os.isFamily(Os.FAMILY_MAC) &&
            !File(nativeResourceFile, "darwin-aarch64/libnucleus_macos.dylib").exists()
    }
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceFile)
    workingDir(nativeDir)
    commandLine("bash", "build.sh")
}

tasks.processResources {
    dependsOn(buildNativeMacOs)
}

tasks.configureEach {
    if (name == "sourcesJar") {
        dependsOn(buildNativeMacOs)
    }
}

mavenPublishing {
    coordinates("io.github.kdroidfilter", "nucleus.decorated-window-jbr", publishVersion)

    pom {
        name.set("Nucleus Decorated Window JBR")
        description.set("JBR-based custom decorated window with native title bar for Compose Desktop")
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
