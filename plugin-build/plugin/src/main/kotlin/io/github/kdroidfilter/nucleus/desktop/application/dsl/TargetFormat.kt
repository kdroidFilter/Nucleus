/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.dsl

import io.github.kdroidfilter.nucleus.internal.utils.OS
import io.github.kdroidfilter.nucleus.internal.utils.currentOS

enum class PackagingBackend {
    /** App-image creation only (jpackage). */
    JPACKAGE,

    /** Full packaging via electron-builder --prepackaged. */
    ELECTRON_BUILDER,
}

enum class TargetFormat(
    internal val id: String,
    internal val targetOS: OS,
    val backend: PackagingBackend,
) {
    // --- Formats using jpackage (app-image only) ---
    AppImage("app-image", currentOS, PackagingBackend.JPACKAGE),

    // --- Existing formats migrated to electron-builder ---
    Deb("deb", OS.Linux, PackagingBackend.ELECTRON_BUILDER),
    Rpm("rpm", OS.Linux, PackagingBackend.ELECTRON_BUILDER),
    Dmg("dmg", OS.MacOS, PackagingBackend.ELECTRON_BUILDER),
    Pkg("pkg", OS.MacOS, PackagingBackend.ELECTRON_BUILDER),
    Exe("exe", OS.Windows, PackagingBackend.ELECTRON_BUILDER),
    Msi("msi", OS.Windows, PackagingBackend.ELECTRON_BUILDER),

    // --- New formats (electron-builder only) ---
    Nsis("nsis", OS.Windows, PackagingBackend.ELECTRON_BUILDER),
    NsisWeb("nsis-web", OS.Windows, PackagingBackend.ELECTRON_BUILDER),
    Portable("portable", OS.Windows, PackagingBackend.ELECTRON_BUILDER),
    AppX("appx", OS.Windows, PackagingBackend.ELECTRON_BUILDER),
    Snap("snap", OS.Linux, PackagingBackend.ELECTRON_BUILDER),
    Flatpak("flatpak", OS.Linux, PackagingBackend.ELECTRON_BUILDER),
    Zip("zip", currentOS, PackagingBackend.ELECTRON_BUILDER),
    Tar("tar.gz", currentOS, PackagingBackend.ELECTRON_BUILDER),
    SevenZ("7z", currentOS, PackagingBackend.ELECTRON_BUILDER),
    ;

    val isCompatibleWithCurrentOS: Boolean by lazy { isCompatibleWith(currentOS) }

    internal fun isCompatibleWith(os: OS): Boolean = os == targetOS

    val outputDirName: String
        get() = if (this == AppImage) "app" else id

    val fileExt: String
        get() {
            check(this != AppImage) { "$this cannot have a file extension" }
            return ".$id"
        }

    /**
     * The electron-builder target name used in CLI arguments.
     * Maps this format to the target identifier expected by electron-builder.
     */
    internal val electronBuilderTarget: String
        get() =
            when (this) {
                Exe, Nsis -> "nsis"
                NsisWeb -> "nsis-web"
                Tar -> "tar.gz"
                SevenZ -> "7z"
                AppImage -> error("AppImage uses jpackage, not electron-builder")
                else -> id
            }
}
