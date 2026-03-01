/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.internal

import io.github.kdroidfilter.nucleus.NucleusExtension
import io.github.kdroidfilter.nucleus.desktop.tasks.AbstractUnpackDefaultApplicationResourcesTask
import io.github.kdroidfilter.nucleus.internal.utils.registerTask
import org.gradle.api.Project

internal fun configureDesktop(
    project: Project,
    nucleusExtension: NucleusExtension,
) {
    if (nucleusExtension.isJvmApplicationInitialized) {
        val appInternal = nucleusExtension.application as JvmApplicationInternal
        val defaultBuildType = appInternal.data.buildTypes.default
        val appData = JvmApplicationContext(project, appInternal, defaultBuildType)
        appData.configureJvmApplication()

        if (appInternal.data.graalvm.isEnabled
                .getOrElse(false)
        ) {
            appData.configureGraalvmApplication()
            appData
                .copy(buildType = appInternal.data.buildTypes.release)
                .configureGraalvmApplication()
        }
    }

    if (nucleusExtension.isNativeApplicationInitialized) {
        val unpackDefaultResources =
            project.registerTask<AbstractUnpackDefaultApplicationResourcesTask>(
                "unpackDefaultNativeApplicationResources",
            ) {}
        configureNativeApplication(project, nucleusExtension.nativeApplication, unpackDefaultResources)
    }
}
