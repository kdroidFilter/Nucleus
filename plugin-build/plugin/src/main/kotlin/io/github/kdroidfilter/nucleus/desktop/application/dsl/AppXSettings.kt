/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.dsl

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass") // Required abstract for Gradle ObjectFactory.newInstance()
abstract class AppXSettings {
    @get:Inject
    internal abstract val objects: ObjectFactory

    /** Application user model ID */
    var applicationId: String? = null

    /** Publisher display name */
    var publisherDisplayName: String? = null

    /** Display name for the Windows Store */
    var displayName: String? = null

    /** Publisher identity (e.g., "CN=MyCompany") */
    var publisher: String? = null

    /** Identity name (e.g., "MyCompany.MyApp") */
    var identityName: String? = null

    /** Languages supported (e.g., "en-US") */
    var languages: List<String>? = null

    /** Add auto-launch on startup capability. Default: false */
    var addAutoLaunchExtension: Boolean = false

    /** Store tile logo (mapped as `StoreLogo.png`) */
    val storeLogo: RegularFileProperty = objects.fileProperty()

    /** Small tile logo (mapped as `Square44x44Logo.png`) */
    val square44x44Logo: RegularFileProperty = objects.fileProperty()

    /** Medium tile logo (mapped as `Square150x150Logo.png`) */
    val square150x150Logo: RegularFileProperty = objects.fileProperty()

    /** Wide tile logo (mapped as `Wide310x150Logo.png`) */
    val wide310x150Logo: RegularFileProperty = objects.fileProperty()
}
