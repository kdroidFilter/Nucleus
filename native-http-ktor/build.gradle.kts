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
    api(project(":native-ssl"))
    api(libs.ktor.client.core)
    compileOnly(libs.ktor.client.cio)
    compileOnly(libs.ktor.client.java)
    compileOnly(libs.ktor.client.okhttp)
    compileOnly(libs.ktor.client.apache5)
    compileOnly(project(":core-runtime"))
    testImplementation(project(":core-runtime"))
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.java)
    testImplementation(libs.junit)
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

mavenPublishing {
    coordinates("io.github.kdroidfilter", "nucleus.native-http-ktor", publishVersion)

    pom {
        name.set("Nucleus Native HTTP Ktor")
        description.set("Engine-agnostic Ktor HttpClient extension with native OS certificate trust")
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
