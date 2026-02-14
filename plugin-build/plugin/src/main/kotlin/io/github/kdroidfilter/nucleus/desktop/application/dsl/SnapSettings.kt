/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.dsl

@Suppress("UnnecessaryAbstractClass") // Required abstract for Gradle ObjectFactory.newInstance()
abstract class SnapSettings {
    /** Confinement level: "strict", "devmode", "classic". Default: "strict" */
    var confinement: String = "strict"

    /** Quality grade: "stable", "devel". Default: "stable" */
    var grade: String = "stable"

    /** Short summary (max 78 chars) */
    var summary: String? = null

    /** Base snap. Default: "core22" */
    var base: String? = null

    /** Snap interfaces (plugs) */
    var plugs: List<String> =
        listOf(
            "desktop",
            "desktop-legacy",
            "home",
            "x11",
            "wayland",
            "unity7",
            "browser-support",
            "network",
            "gsettings",
            "audio-playback",
            "opengl",
        )

    /** Auto-start on login. Default: false */
    var autoStart: Boolean = false

    /** Compression algorithm: "xz" or "lzo". Default: "xz" */
    var compression: String? = null
}
