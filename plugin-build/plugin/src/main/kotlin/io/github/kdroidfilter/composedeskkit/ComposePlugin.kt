/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:Suppress("unused")

package io.github.kdroidfilter.composedeskkit

import groovy.lang.Closure
import io.github.kdroidfilter.composedeskkit.desktop.DesktopExtension
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.configureDesktop
import io.github.kdroidfilter.composedeskkit.experimental.internal.configureExperimentalTargetsFlagsCheck
import io.github.kdroidfilter.composedeskkit.internal.KOTLIN_MPP_PLUGIN_ID
import io.github.kdroidfilter.composedeskkit.internal.mppExt
import io.github.kdroidfilter.composedeskkit.internal.utils.currentTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

internal val composeVersion get() = ComposeBuildConfig.composeVersion
internal val composeMaterial3Version get() = ComposeBuildConfig.composeMaterial3Version

abstract class ComposePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val composeExtension = project.extensions.create("composeDeskKit", ComposeExtension::class.java, project)
        val desktopExtension = composeExtension.extensions.create("desktop", DesktopExtension::class.java)
        project.tasks.register("templateExample", TemplateExampleTask::class.java)

        if ((project.dependencies as? ExtensionAware)?.extensions?.findByName("composeDeskKit") == null) {
            project.dependencies.extensions.add("composeDeskKit", Dependencies(project))
        }

        if (!project.buildFile.endsWith(".gradle.kts")) {
            setUpGroovyDslExtensions(project)
        }

        project.checkComposeCompilerPlugin()

        project.configureRuntimeLibrariesCompatibilityCheck()

        project.afterEvaluate {
            configureDesktop(project, desktopExtension)
            project.plugins.withId(KOTLIN_MPP_PLUGIN_ID) {
                val mppExt = project.mppExt
                project.configureExperimentalTargetsFlagsCheck(mppExt)
            }
        }
    }

    @Suppress("DEPRECATION")
    class Dependencies(
        project: Project,
    ) {
        val desktop = DesktopDependencies

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.animation:animation:\${ComposeBuildConfig.composeVersion}\""),
        )
        val animation get() = composeDependency("org.jetbrains.compose.animation:animation")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.animation:animation-graphics:\${ComposeBuildConfig.composeVersion}\""),
        )
        val animationGraphics get() = composeDependency("org.jetbrains.compose.animation:animation-graphics")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.foundation:foundation:\${ComposeBuildConfig.composeVersion}\""),
        )
        val foundation get() = composeDependency("org.jetbrains.compose.foundation:foundation")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.material:material:\${ComposeBuildConfig.composeVersion}\""),
        )
        val material get() = composeDependency("org.jetbrains.compose.material:material")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.material3:material3:\${ComposeBuildConfig.composeMaterial3Version}\""),
        )
        val material3 get() = composeMaterial3Dependency("org.jetbrains.compose.material3:material3")

        @Deprecated(
            "Specify dependency directly",
            replaceWith =
                ReplaceWith(
                    "\"org.jetbrains.compose.material3:material3-adaptive-navigation-suite:\${ComposeBuildConfig.composeMaterial3Version}\"",
                ),
        )
        val material3AdaptiveNavigationSuite get() =
            composeMaterial3Dependency(
                "org.jetbrains.compose.material3:material3-adaptive-navigation-suite",
            )

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.runtime:runtime:\${ComposeBuildConfig.composeVersion}\""),
        )
        val runtime get() = composeDependency("org.jetbrains.compose.runtime:runtime")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.runtime:runtime-saveable:\${ComposeBuildConfig.composeVersion}\""),
        )
        val runtimeSaveable get() = composeDependency("org.jetbrains.compose.runtime:runtime-saveable")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.ui:ui:\${ComposeBuildConfig.composeVersion}\""),
        )
        val ui get() = composeDependency("org.jetbrains.compose.ui:ui")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.ui:ui-test:\${ComposeBuildConfig.composeVersion}\""),
        )
        @ExperimentalComposeLibrary
        val uiTest get() = composeDependency("org.jetbrains.compose.ui:ui-test")

        @Deprecated(
            "Use org.jetbrains.compose.ui:ui-tooling module instead",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.ui:ui-tooling:\${ComposeBuildConfig.composeVersion}\""),
        )
        val uiTooling get() = composeDependency("org.jetbrains.compose.ui:ui-tooling")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.ui:ui-util:\${ComposeBuildConfig.composeVersion}\""),
        )
        val uiUtil get() = composeDependency("org.jetbrains.compose.ui:ui-util")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.ui:ui-tooling-preview:\${ComposeBuildConfig.composeVersion}\""),
        )
        val preview get() = composeDependency("org.jetbrains.compose.ui:ui-tooling-preview")

        @Deprecated(
            "This artifact is pinned to version 1.7.3 and will not receive updates. " +
                "Either use this version explicitly or migrate to Material Symbols (vector resources). " +
                "See https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.material:material-icons-extended:1.7.3\""),
        )
        val materialIconsExtended get() = "org.jetbrains.compose.material:material-icons-extended:1.7.3"

        @Deprecated("Specify dependency directly")
        val components get() = CommonComponentsDependencies
    }

    @Deprecated("Specify dependency directly")
    object DesktopDependencies {
        @Deprecated("Specify dependency directly")
        val components = DesktopComponentsDependencies

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.desktop:desktop:\${ComposeBuildConfig.composeVersion}\""),
        )
        val common = composeDependency("org.jetbrains.compose.desktop:desktop")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.desktop:desktop-jvm-linux-x64:\${ComposeBuildConfig.composeVersion}\""),
        )
        val linux_x64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-linux-x64")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:\${ComposeBuildConfig.composeVersion}\""),
        )
        val linux_arm64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-linux-arm64")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.desktop:desktop-jvm-windows-x64:\${ComposeBuildConfig.composeVersion}\""),
        )
        val windows_x64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-windows-x64")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.desktop:desktop-jvm-windows-arm64:\${ComposeBuildConfig.composeVersion}\""),
        )
        val windows_arm64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-windows-arm64")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.desktop:desktop-jvm-macos-x64:\${ComposeBuildConfig.composeVersion}\""),
        )
        val macos_x64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-macos-x64")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:\${ComposeBuildConfig.composeVersion}\""),
        )
        val macos_arm64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.ui:ui-test-junit4:\${ComposeBuildConfig.composeVersion}\""),
        )
        val uiTestJUnit4 get() = composeDependency("org.jetbrains.compose.ui:ui-test-junit4")

        val currentOs by lazy {
            composeDependency("org.jetbrains.compose.desktop:desktop-jvm-${currentTarget.id}")
        }
    }

    @Deprecated("Specify dependency directly")
    object CommonComponentsDependencies {
        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.components:components-resources:\${ComposeBuildConfig.composeVersion}\""),
        )
        val resources = composeDependency("org.jetbrains.compose.components:components-resources")

        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.ui:ui-tooling-preview:\${ComposeBuildConfig.composeVersion}\""),
        )
        val uiToolingPreview = composeDependency("org.jetbrains.compose.components:components-ui-tooling-preview")
    }

    @Deprecated("Specify dependency directly")
    object DesktopComponentsDependencies {
        @Deprecated(
            "Specify dependency directly",
            replaceWith = ReplaceWith("\"org.jetbrains.compose.components:components-splitpane:\${ComposeBuildConfig.composeVersion}\""),
        )
        @ExperimentalComposeLibrary
        val splitPane = composeDependency("org.jetbrains.compose.components:components-splitpane")

        @Deprecated(
            "Specify dependency directly",
            replaceWith =
                ReplaceWith(
                    "\"org.jetbrains.compose.components:components-animatedimage:\${ComposeBuildConfig.composeVersion}\"",
                ),
        )
        @ExperimentalComposeLibrary
        val animatedImage = composeDependency("org.jetbrains.compose.components:components-animatedimage")
    }
}

fun RepositoryHandler.jetbrainsCompose(): MavenArtifactRepository =
    maven { repo -> repo.setUrl("https://packages.jetbrains.team/maven/p/cmp/dev") }

fun KotlinDependencyHandler.compose(groupWithArtifact: String) = composeDependency(groupWithArtifact)

fun DependencyHandler.compose(groupWithArtifact: String) = composeDependency(groupWithArtifact)

private fun composeDependency(groupWithArtifact: String) = "$groupWithArtifact:$composeVersion"

private fun composeMaterial3Dependency(groupWithArtifact: String) = "$groupWithArtifact:$composeMaterial3Version"

private fun setUpGroovyDslExtensions(project: Project) {
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        (project.extensions.getByName("kotlin") as? ExtensionAware)?.apply {
            if (extensions.findByName("composeDeskKit") == null) {
                extensions.add("composeDeskKit", ComposePlugin.Dependencies(project))
            }
        }
    }
    (project.repositories as? ExtensionAware)?.extensions?.apply {
        if (findByName("jetbrainsCompose") == null) {
            add(
                "jetbrainsCompose",
                object : Closure<MavenArtifactRepository>(project.repositories) {
                    fun doCall(): MavenArtifactRepository = project.repositories.jetbrainsCompose()
                },
            )
        }
    }
}
