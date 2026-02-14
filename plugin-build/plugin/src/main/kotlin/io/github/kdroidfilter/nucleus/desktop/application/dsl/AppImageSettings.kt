/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.dsl

@Suppress("UnnecessaryAbstractClass") // Required abstract for Gradle ObjectFactory.newInstance()
abstract class AppImageSettings {
    /** Desktop file category (e.g., "Utility", "Development") */
    var category: String? = null

    /** Desktop file generic name */
    var genericName: String? = null

    /** Synopsis (short description for .desktop) */
    var synopsis: String? = null

    /** Additional desktop file entries (key=value) */
    var desktopEntries: Map<String, String> = emptyMap()
}
