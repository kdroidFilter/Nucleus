/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.dsl

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class UniversalBinarySettings {
    @get:Inject
    internal abstract val objects: ObjectFactory

    /** Enable universal binary (arm64 + x64) packaging for macOS. */
    var enabled: Boolean = false

    /** Path to a macOS x64 JDK (e.g. Liberica Full JDK x64). Required when enabled = true. */
    var x64JdkPath: String? = null
}
