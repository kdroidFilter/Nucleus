/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.dsl

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class MsixSettings {
    @get:Inject
    internal abstract val objects: ObjectFactory

    val iconFile: RegularFileProperty = objects.fileProperty()
    val signingPfxFile: RegularFileProperty = objects.fileProperty()
    val manifestTemplateFile: RegularFileProperty = objects.fileProperty()

    var signingPassword: String? = null
    var identityName: String? = null
    var publisher: String? = null
    var publisherDisplayName: String? = null
    var displayName: String? = null
    var description: String? = null
    var backgroundColor: String = "transparent"
    var appId: String = "App"
    var appExecutable: String? = null
    var processorArchitecture: String? = null
    var targetDeviceFamilyName: String = "Windows.Desktop"
    var targetDeviceFamilyMinVersion: String = "10.0.17763.0"
    var targetDeviceFamilyMaxVersionTested: String = "10.0.22621.2861"
}
