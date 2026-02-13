/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.internal

import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.PackagingBackend
import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.TargetFormat
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.validation.validatePackageVersions
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractCheckNativeDistributionRuntime
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractElectronBuilderPackageTask
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractGenerateAotCacheTask
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractJLinkTask
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractJPackageTask
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractNotarizationTask
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractProguardTask
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractRunDistributableTask
import io.github.kdroidfilter.composedeskkit.desktop.application.tasks.AbstractSuggestModulesTask
import io.github.kdroidfilter.composedeskkit.desktop.tasks.AbstractJarsFlattenTask
import io.github.kdroidfilter.composedeskkit.desktop.tasks.AbstractUnpackDefaultComposeApplicationResourcesTask
import io.github.kdroidfilter.composedeskkit.internal.utils.OS
import io.github.kdroidfilter.composedeskkit.internal.utils.currentOS
import io.github.kdroidfilter.composedeskkit.internal.utils.currentTarget
import io.github.kdroidfilter.composedeskkit.internal.utils.dependsOn
import io.github.kdroidfilter.composedeskkit.internal.utils.detachedComposeGradleDependency
import io.github.kdroidfilter.composedeskkit.internal.utils.detachedDependency
import io.github.kdroidfilter.composedeskkit.internal.utils.dir
import io.github.kdroidfilter.composedeskkit.internal.utils.excludeTransitiveDependencies
import io.github.kdroidfilter.composedeskkit.internal.utils.file
import io.github.kdroidfilter.composedeskkit.internal.utils.ioFile
import io.github.kdroidfilter.composedeskkit.internal.utils.ioFileOrNull
import io.github.kdroidfilter.composedeskkit.internal.utils.javaExecutable
import io.github.kdroidfilter.composedeskkit.internal.utils.provider
import org.gradle.api.DefaultTask
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

private val defaultJvmArgs = listOf("-D$CONFIGURE_SWING_GLOBALS=true")
internal const val COMPOSE_DESKTOP_TASK_GROUP = "compose desktop"

// todo: multiple launchers
// todo: file associations
// todo: use workers
internal fun JvmApplicationContext.configureJvmApplication() {
    if (app.isDefaultConfigurationEnabled) {
        configureDefaultApp()
    }

    if (app.nativeDistributions.cleanupNativeLibs) {
        registerCleanNativeLibsTransform(project)
    }

    validatePackageVersions()
    val commonTasks = configureCommonJvmDesktopTasks()
    configurePackagingTasks(commonTasks)
    copy(buildType = app.buildTypes.release).configurePackagingTasks(commonTasks)
}

internal class CommonJvmDesktopTasks(
    val unpackDefaultResources: TaskProvider<AbstractUnpackDefaultComposeApplicationResourcesTask>,
    val checkRuntime: TaskProvider<AbstractCheckNativeDistributionRuntime>,
    val suggestRuntimeModules: TaskProvider<AbstractSuggestModulesTask>,
    val prepareAppResources: TaskProvider<Sync>,
    val createRuntimeImage: TaskProvider<AbstractJLinkTask>,
)

private fun JvmApplicationContext.configureCommonJvmDesktopTasks(): CommonJvmDesktopTasks {
    val unpackDefaultResources =
        tasks.register<AbstractUnpackDefaultComposeApplicationResourcesTask>(
            taskNameAction = "unpack",
            taskNameObject = "DefaultComposeDesktopJvmApplicationResources",
        ) {}

    val checkRuntime =
        tasks.register<AbstractCheckNativeDistributionRuntime>(
            taskNameAction = "check",
            taskNameObject = "runtime",
        ) {
            jdkHome.set(app.javaHomeProvider)
            checkJdkVendor.set(ComposeProperties.checkJdkVendor(project.providers))
            jdkVersionProbeJar.from(
                project
                    .detachedComposeGradleDependency(
                        artifactId = "gradle-plugin-internal-jdk-version-probe",
                    ).excludeTransitiveDependencies(),
            )
        }

    val suggestRuntimeModules =
        tasks.register<AbstractSuggestModulesTask>(
            taskNameAction = "suggest",
            taskNameObject = "runtimeModules",
        ) {
            dependsOn(checkRuntime)
            javaHome.set(app.javaHomeProvider)
            modules.set(provider { app.nativeDistributions.modules })

            useAppRuntimeFiles { (jarFiles, mainJar) ->
                files.from(jarFiles)
                launcherMainJar.set(mainJar)
            }
        }

    val prepareAppResources =
        tasks.register<Sync>(
            taskNameAction = "prepare",
            taskNameObject = "appResources",
        ) {
            val appResourcesRootDir = app.nativeDistributions.appResourcesRootDir
            if (appResourcesRootDir.isPresent) {
                from(appResourcesRootDir.dir("common"))
                from(appResourcesRootDir.dir(currentOS.id))
                from(appResourcesRootDir.dir(currentTarget.id))
            }
            into(jvmTmpDirForTask())
        }

    val createRuntimeImage =
        tasks.register<AbstractJLinkTask>(
            taskNameAction = "create",
            taskNameObject = "runtimeImage",
        ) {
            dependsOn(checkRuntime)
            javaHome.set(app.javaHomeProvider)
            modules.set(provider { app.nativeDistributions.modules })
            includeAllModules.set(provider { app.nativeDistributions.includeAllModules })
            javaRuntimePropertiesFile.set(checkRuntime.flatMap { it.javaRuntimePropertiesFile })
            destinationDir.set(appTmpDir.dir("runtime"))
        }

    return CommonJvmDesktopTasks(
        unpackDefaultResources,
        checkRuntime,
        suggestRuntimeModules,
        prepareAppResources,
        createRuntimeImage,
    )
}

private fun JvmApplicationContext.configurePackagingTasks(commonTasks: CommonJvmDesktopTasks) {
    val runProguard =
        if (buildType.proguard.isEnabled.orNull == true) {
            tasks.register<AbstractProguardTask>(
                taskNameAction = "proguard",
                taskNameObject = "Jars",
            ) {
                configureProguardTask(this, commonTasks.unpackDefaultResources)
            }
        } else {
            null
        }

    val createDistributable =
        tasks.register<AbstractJPackageTask>(
            taskNameAction = "create",
            taskNameObject = "distributable",
            args = listOf(TargetFormat.AppImage),
        ) {
            configurePackageTask(
                this,
                createRuntimeImage = commonTasks.createRuntimeImage,
                prepareAppResources = commonTasks.prepareAppResources,
                checkRuntime = commonTasks.checkRuntime,
                unpackDefaultResources = commonTasks.unpackDefaultResources,
                runProguard = runProguard,
            )
        }

    val generateAotCache =
        if (app.nativeDistributions.enableAotCache) {
            tasks.register<AbstractGenerateAotCacheTask>(
                taskNameAction = "generate",
                taskNameObject = "AotCache",
            ) {
                dependsOn(createDistributable)
                distributableDir.set(createDistributable.flatMap { it.destinationDir })
                javaHome.set(app.javaHomeProvider)
                javaRuntimePropertiesFile.set(commonTasks.checkRuntime.flatMap { it.javaRuntimePropertiesFile })
            }
        } else {
            null
        }

    val packageFormats =
        app.nativeDistributions.targetFormats
            .filter { it.backend == PackagingBackend.ELECTRON_BUILDER }
            .map { targetFormat ->
                val packageFormat =
                    tasks.register<AbstractElectronBuilderPackageTask>(
                        taskNameAction = "package",
                        taskNameObject = targetFormat.name,
                        args = listOf(targetFormat),
                    ) {
                        configureElectronBuilderPackageTask(
                            this,
                            createDistributable = createDistributable,
                        )
                        generateAotCache?.let { dependsOn(it) }
                    }

                if (targetFormat.isCompatibleWith(OS.MacOS)) {
                    tasks.register<AbstractNotarizationTask>(
                        taskNameAction = "notarize",
                        taskNameObject = targetFormat.name,
                        args = listOf(targetFormat),
                    ) {
                        dependsOn(packageFormat)
                        inputDir.set(packageFormat.flatMap { it.destinationDir })
                        configureCommonNotarizationSettings(this)
                    }
                }

                packageFormat
            }

    val packageForCurrentOS =
        tasks.register<DefaultTask>(
            taskNameAction = "package",
            taskNameObject = "distributionForCurrentOS",
        ) {
            dependsOn(packageFormats)
        }

    if (buildType === app.buildTypes.default) {
        // todo: remove
        tasks.register<DefaultTask>("package") {
            dependsOn(packageForCurrentOS)

            doLast {
                it.logger.error(
                    "'${it.name}' task is deprecated and will be removed in next releases. " +
                        "Use '${packageForCurrentOS.get().name}' task instead",
                )
            }
        }
    }

    val flattenJars =
        tasks.register<AbstractJarsFlattenTask>(
            taskNameAction = "flatten",
            taskNameObject = "Jars",
        ) {
            configureFlattenJars(this, runProguard)
        }

    val packageUberJarForCurrentOS =
        tasks.register<Jar>(
            taskNameAction = "package",
            taskNameObject = "uberJarForCurrentOS",
        ) {
            configurePackageUberJarForCurrentOS(this, flattenJars)
        }

    val runDistributable =
        tasks.register<AbstractRunDistributableTask>(
            taskNameAction = "run",
            taskNameObject = "distributable",
            args = listOf(createDistributable),
        )
    if (generateAotCache != null) {
        runDistributable.dependsOn(generateAotCache)
    }

    val run =
        tasks.register<JavaExec>(taskNameAction = "run") {
            configureRunTask(this, commonTasks.prepareAppResources, runProguard)
        }
}

private fun JvmApplicationContext.configureProguardTask(
    proguard: AbstractProguardTask,
    unpackDefaultResources: TaskProvider<AbstractUnpackDefaultComposeApplicationResourcesTask>,
): AbstractProguardTask =
    proguard.apply {
        val settings = buildType.proguard
        mainClass.set(app.mainClass)
        proguardVersion.set(settings.version)
        proguardFiles.from(
            proguardVersion.map { proguardVersion ->
                project.detachedDependency(groupId = "com.guardsquare", artifactId = "proguard-gradle", version = proguardVersion)
            },
        )
        configurationFiles.from(settings.configurationFiles)
        // ProGuard uses -dontobfuscate option to turn off obfuscation, which is enabled by default
        // We want to disable obfuscation by default, because often
        // it is not needed, but makes troubleshooting much harder.
        // If obfuscation is turned off by default,
        // enabling (`isObfuscationEnabled.set(true)`) seems much better,
        // than disabling obfuscation disabling (`dontObfuscate.set(false)`).
        // That's why a task property is follows ProGuard design,
        // when our DSL does the opposite.
        dontobfuscate.set(settings.obfuscate.map { !it })
        dontoptimize.set(settings.optimize.map { !it })

        joinOutputJars.set(settings.joinOutputJars)

        dependsOn(unpackDefaultResources)
        defaultComposeRulesFile.set(unpackDefaultResources.flatMap { it.resources.defaultComposeProguardRules })

        maxHeapSize.set(settings.maxHeapSize)
        destinationDir.set(appTmpDir.dir("proguard"))
        javaHome.set(app.javaHomeProvider)

        useAppRuntimeFiles { files ->
            inputFiles.from(files.allRuntimeJars)
            mainJar.set(files.mainJar)
        }
    }

@Suppress("LongParameterList")
private fun JvmApplicationContext.configurePackageTask(
    packageTask: AbstractJPackageTask,
    createRuntimeImage: TaskProvider<AbstractJLinkTask>? = null,
    prepareAppResources: TaskProvider<Sync>? = null,
    checkRuntime: TaskProvider<AbstractCheckNativeDistributionRuntime>? = null,
    unpackDefaultResources: TaskProvider<AbstractUnpackDefaultComposeApplicationResourcesTask>,
    runProguard: Provider<AbstractProguardTask>? = null,
) {
    packageTask.enabled = packageTask.targetFormat.isCompatibleWithCurrentOS

    createRuntimeImage?.let { createRuntimeImage ->
        packageTask.dependsOn(createRuntimeImage)
        packageTask.runtimeImage.set(createRuntimeImage.flatMap { it.destinationDir })
    }

    prepareAppResources?.let { prepareResources ->
        packageTask.dependsOn(prepareResources)
        val resourcesDir = packageTask.project.layout.dir(prepareResources.map { it.destinationDir })
        packageTask.appResourcesDir.set(resourcesDir)
    }

    checkRuntime?.let { checkRuntime ->
        packageTask.dependsOn(checkRuntime)
        packageTask.javaRuntimePropertiesFile.set(checkRuntime.flatMap { it.javaRuntimePropertiesFile })
    }

    this.configurePlatformSettings(packageTask, unpackDefaultResources)

    app.nativeDistributions.let { executables ->
        packageTask.packageName.set(packageNameProvider)
        packageTask.packageDescription.set(packageTask.provider { executables.description })
        packageTask.packageCopyright.set(packageTask.provider { executables.copyright })
        packageTask.packageVendor.set(packageTask.provider { executables.vendor })
        packageTask.packageVersion.set(packageVersionFor(packageTask.targetFormat))
    }

    packageTask.destinationDir.set(
        app.nativeDistributions.outputBaseDir.map {
            it.dir("$appDirName/${packageTask.targetFormat.outputDirName}")
        },
    )
    packageTask.javaHome.set(app.javaHomeProvider)

    if (runProguard != null) {
        packageTask.dependsOn(runProguard)
        packageTask.files.from(project.fileTree(runProguard.flatMap { it.destinationDir }))
        packageTask.launcherMainJar.set(runProguard.flatMap { it.mainJarInDestinationDir })
        packageTask.mangleJarFilesNames.set(false)
        packageTask.packageFromUberJar.set(runProguard.flatMap { it.joinOutputJars })
    } else {
        packageTask.useAppRuntimeFiles { (runtimeJars, mainJar) ->
            files.from(runtimeJars)
            launcherMainJar.set(mainJar)
        }
    }

    packageTask.launcherMainClass.set(provider { app.mainClass })
    packageTask.launcherJvmArgs.set(
        provider {
            val executableTypeArg = "-D$APP_EXECUTABLE_TYPE=${packageTask.targetFormat.executableTypeValue}"
            val args = defaultJvmArgs + executableTypeArg + app.jvmArgs
            val splash = app.nativeDistributions.splashImage
            if (splash != null) args + "-splash:\$APPDIR/resources/$splash" else args
        },
    )
    packageTask.launcherArgs.set(provider { app.args })
}

private fun JvmApplicationContext.configureElectronBuilderPackageTask(
    packageTask: AbstractElectronBuilderPackageTask,
    createDistributable: TaskProvider<AbstractJPackageTask>,
) {
    packageTask.enabled = packageTask.targetFormat.isCompatibleWithCurrentOS
    packageTask.dependsOn(createDistributable)
    packageTask.appImageRoot.set(createDistributable.flatMap { it.destinationDir })

    packageTask.destinationDir.set(
        app.nativeDistributions.outputBaseDir.map {
            it.dir("$appDirName/${packageTask.targetFormat.outputDirName}")
        },
    )

    packageTask.packageName.set(packageNameProvider)
    packageTask.packageVersion.set(packageVersionFor(packageTask.targetFormat))
    packageTask.customNodePath.set(ComposeProperties.electronBuilderNodePath(project.providers))
    packageTask.distributions = app.nativeDistributions
}

internal fun JvmApplicationContext.configureCommonNotarizationSettings(notarizationTask: AbstractNotarizationTask) {
    notarizationTask.nonValidatedNotarizationSettings = app.nativeDistributions.macOS.notarization
}

private fun <T> TaskProvider<AbstractUnpackDefaultComposeApplicationResourcesTask>.get(
    fn: AbstractUnpackDefaultComposeApplicationResourcesTask.DefaultResourcesProvider.() -> Provider<T>,
) = flatMap { fn(it.resources) }

internal fun JvmApplicationContext.configurePlatformSettings(
    packageTask: AbstractJPackageTask,
    defaultResources: TaskProvider<AbstractUnpackDefaultComposeApplicationResourcesTask>,
) {
    packageTask.dependsOn(defaultResources)

    when (currentOS) {
        OS.Linux -> {
            app.nativeDistributions.linux.also { linux ->
                packageTask.iconFile.set(linux.iconFile.orElse(defaultResources.get { linuxIcon }))
                packageTask.fileAssociations.set(provider { linux.fileAssociations })
            }
        }
        OS.Windows -> {
            app.nativeDistributions.windows.also { win ->
                packageTask.winConsole.set(provider { win.console })
                packageTask.iconFile.set(win.iconFile.orElse(defaultResources.get { windowsIcon }))
                packageTask.fileAssociations.set(provider { win.fileAssociations })
            }
        }
        OS.MacOS -> {
            app.nativeDistributions.macOS.also { mac ->
                packageTask.macPackageName.set(provider { mac.packageName })
                packageTask.macDockName.set(
                    if (mac.setDockNameSameAsPackageName) {
                        provider { mac.dockName }
                            .orElse(packageTask.macPackageName)
                            .orElse(packageTask.packageName)
                    } else {
                        provider { mac.dockName }
                    },
                )
                packageTask.macAppStore.set(mac.appStore)
                packageTask.macAppCategory.set(mac.appCategory)
                packageTask.macMinimumSystemVersion.set(mac.minimumSystemVersion)
                val defaultEntitlements = defaultResources.get { defaultEntitlements }
                packageTask.macEntitlementsFile.set(mac.entitlementsFile.orElse(defaultEntitlements))
                packageTask.macRuntimeEntitlementsFile.set(mac.runtimeEntitlementsFile.orElse(defaultEntitlements))
                packageTask.packageBuildVersion.set(packageBuildVersionFor(packageTask.targetFormat))
                packageTask.nonValidatedMacBundleID.set(provider { mac.bundleID })
                packageTask.macProvisioningProfile.set(mac.provisioningProfile)
                packageTask.macRuntimeProvisioningProfile.set(mac.runtimeProvisioningProfile)
                packageTask.macExtraPlistKeysRawXml.set(provider { mac.infoPlistSettings.extraKeysRawXml })
                packageTask.nonValidatedMacSigningSettings = app.nativeDistributions.macOS.signing
                packageTask.iconFile.set(mac.iconFile.orElse(defaultResources.get { macIcon }))
                packageTask.fileAssociations.set(provider { mac.fileAssociations })
                packageTask.macLayeredIcons.set(mac.layeredIconDir)
            }
        }
    }
}

private fun JvmApplicationContext.configureRunTask(
    exec: JavaExec,
    prepareAppResources: TaskProvider<Sync>,
    runProguard: Provider<AbstractProguardTask>?,
) {
    exec.dependsOn(prepareAppResources)

    exec.mainClass.set(exec.provider { app.mainClass })
    exec.executable(javaExecutable(app.javaHome))
    exec.jvmArgs =
        arrayListOf<String>().apply {
            addAll(defaultJvmArgs)
            add("-D$APP_EXECUTABLE_TYPE=$EXECUTABLE_TYPE_DEV")

            if (currentOS == OS.MacOS) {
                val file = app.nativeDistributions.macOS.iconFile.ioFileOrNull
                if (file != null) add("-Xdock:icon=$file")
            }

            addAll(app.jvmArgs)
            val appResourcesDir = prepareAppResources.get().destinationDir
            add("-D$APP_RESOURCES_DIR=${appResourcesDir.absolutePath}")

            app.nativeDistributions.splashImage?.let { splash ->
                val splashFile = appResourcesDir.resolve(splash)
                if (splashFile.exists()) {
                    add("-splash:${splashFile.absolutePath}")
                }
            }

            // Dev mode AOT: ./gradlew run -Paot=train|on|auto|off
            val aotCacheDir =
                project.layout.buildDirectory
                    .dir("compose/aot-cache")
                    .get()
                    .asFile
            val devAotCache = java.io.File(aotCacheDir, "dev.aot")
            when (project.findProperty("aot")?.toString()) {
                "train" -> {
                    aotCacheDir.mkdirs()
                    add("-XX:AOTCacheOutput=${devAotCache.absolutePath}")
                }
                "on" -> {
                    if (devAotCache.exists()) {
                        add("-XX:AOTCache=${devAotCache.absolutePath}")
                    }
                }
                "auto" -> {
                    if (devAotCache.exists()) {
                        add("-XX:AOTCache=${devAotCache.absolutePath}")
                    } else {
                        aotCacheDir.mkdirs()
                        add("-XX:AOTCacheOutput=${devAotCache.absolutePath}")
                    }
                }
                // "off" or absent → no-op
            }
        }
    exec.args = app.args

    if (runProguard != null) {
        exec.dependsOn(runProguard)
        exec.classpath = project.fileTree(runProguard.flatMap { it.destinationDir })
    } else {
        exec.useAppRuntimeFiles { (runtimeJars, _) ->
            classpath = runtimeJars
        }
    }
}

private fun JvmApplicationContext.configureFlattenJars(
    flattenJars: AbstractJarsFlattenTask,
    runProguard: Provider<AbstractProguardTask>?,
) {
    if (runProguard != null) {
        flattenJars.dependsOn(runProguard)
        flattenJars.inputFiles.from(runProguard.flatMap { it.destinationDir })
    } else {
        flattenJars.useAppRuntimeFiles { (runtimeJars, _) ->
            inputFiles.from(runtimeJars)
        }
    }

    flattenJars.flattenedJar.set(appTmpDir.file("flattenJars/flattened.jar"))
}

private fun JvmApplicationContext.configurePackageUberJarForCurrentOS(
    jar: Jar,
    flattenJars: Provider<AbstractJarsFlattenTask>,
) {
    jar.dependsOn(flattenJars)
    jar.from(project.zipTree(flattenJars.flatMap { it.flattenedJar }))

    app.mainClass?.let { jar.manifest.attributes["Main-Class"] = it }
    jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    jar.archiveAppendix.set(currentTarget.id)
    jar.archiveBaseName.set(packageNameProvider)
    jar.archiveVersion.set(packageVersionFor(TargetFormat.AppImage))
    jar.archiveClassifier.set(buildType.classifier)
    jar.destinationDirectory.set(
        jar.project.layout.buildDirectory
            .dir("compose/jars"),
    )

    jar.doLast {
        jar.logger.lifecycle("The jar is written to ${jar.archiveFile.ioFile.canonicalPath}")
    }
}
