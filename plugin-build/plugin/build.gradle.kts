import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())

    compileOnly(localGroovy())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(kotlin("native-utils"))
    compileOnly(libs.agp)
    compileOnly(libs.agp.api)

    implementation(libs.download.task)
    implementation(libs.kotlin.poet)

    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-opt-in=io.github.kdroidfilter.composedeskkit.ExperimentalComposeLibrary")
    }
}

// BuildConfig generation
val buildConfigDir
    get() = project.layout.buildDirectory.dir("generated/buildconfig")
val composeVersion = project.findProperty("compose.version")?.toString() ?: "1.10.0"
val composeMaterial3Version = project.findProperty("compose.material3.version")?.toString() ?: "1.9.0"
val pluginVersion = project.property("VERSION").toString()
val buildConfig = tasks.register("buildConfig", GenerateBuildConfig::class.java) {
    classFqName.set("io.github.kdroidfilter.composedeskkit.ComposeBuildConfig")
    generatedOutputDir.set(buildConfigDir)
    fieldsToGenerate.put("composeVersion", composeVersion)
    fieldsToGenerate.put("composeMaterial3Version", composeMaterial3Version)
    fieldsToGenerate.put("composeGradlePluginVersion", pluginVersion)
}
tasks.named("compileKotlin", KotlinCompilationTask::class) {
    dependsOn(buildConfig)
}
sourceSets.main.configure {
    java.srcDir(buildConfig.flatMap { it.generatedOutputDir })
}

gradlePlugin {
    plugins {
        create(property("ID").toString()) {
            id = property("ID").toString()
            implementationClass = property("IMPLEMENTATION_CLASS").toString()
            version = property("VERSION").toString()
            description = property("DESCRIPTION").toString()
            displayName = property("DISPLAY_NAME").toString()
            tags.set(listOf("compose", "desktop", "ui-framework"))
        }
    }
}

gradlePlugin {
    website.set(property("WEBSITE").toString())
    vcsUrl.set(property("VCS_URL").toString())
}

// Use Detekt with type resolution for check
tasks.named("check").configure {
    this.setDependsOn(
        this.dependsOn.filterNot {
            it is TaskProvider<*> && it.name == "detekt"
        } + tasks.named("detektMain"),
    )
}

tasks.create("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}
