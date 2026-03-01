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
    implementation(kotlin("stdlib"))
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

val buildNativeLinux by tasks.registering(Exec::class) {
    description = "Compiles the C JNI bridge into Linux shared libraries (x64 + aarch64)"
    group = "build"
    val nativeDir = file("src/main/native/linux")
    onlyIf {
        Os.isFamily(Os.FAMILY_UNIX) &&
            !Os.isFamily(Os.FAMILY_MAC) &&
            !File(nativeResourceFile, "linux-x64/libnucleus_linux_hidpi_jni.so").exists() &&
            !File(nativeResourceFile, "linux-aarch64/libnucleus_linux_hidpi_jni.so").exists()
    }
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceFile)
    workingDir(nativeDir)
    commandLine("bash", "build.sh")
}

tasks.processResources {
    dependsOn(buildNativeLinux)
}

tasks.configureEach {
    if (name == "sourcesJar") {
        dependsOn(buildNativeLinux)
    }
}

mavenPublishing {
    coordinates("io.github.kdroidfilter", "nucleus.linux-hidpi", publishVersion)

    pom {
        name.set("Nucleus Linux HiDPI")
        description.set("Native HiDPI scale factor detection for Compose Desktop on Linux (GSettings, GDK_SCALE, Xft.dpi)")
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
