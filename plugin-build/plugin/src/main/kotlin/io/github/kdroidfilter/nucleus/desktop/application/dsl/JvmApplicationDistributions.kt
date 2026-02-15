/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.dsl

import org.gradle.api.Action
import java.io.File

internal val DEFAULT_RUNTIME_MODULES =
    arrayOf(
        "java.base",
        "java.desktop",
        "java.logging",
        "jdk.crypto.ec",
    )

abstract class JvmApplicationDistributions : AbstractDistributions() {
    var modules = arrayListOf(*DEFAULT_RUNTIME_MODULES)

    fun modules(vararg modules: String) {
        this.modules.addAll(modules.toList())
    }

    var includeAllModules: Boolean = false

    /** Strip native libraries for non-target platforms from dependency JARs to reduce package size. */
    var cleanupNativeLibs: Boolean = false

    /** Splash screen image filename relative to appResources (e.g. "splash.png"). */
    var splashImage: String? = null

    /** Enable JDK 25+ AOT cache generation for faster application startup. */
    var enableAotCache: Boolean = false

    /**
     * Enable App Sandbox compatibility for macOS App Store distribution.
     *
     * When enabled, the plugin:
     * 1. Extracts native libraries (.dylib, .jnilib, .so, .dll) from dependency JARs
     *    and places them in the app resources directory so they get signed automatically.
     * 2. Strips native libraries from the packaged JARs to avoid duplication.
     * 3. Adds JVM args to load native libs from the resources directory instead of
     *    extracting them at runtime (which fails in sandboxed environments).
     * 4. Auto-detects JNA in the classpath and configures it to skip runtime extraction.
     * 5. Signs native libraries in the resources directory individually on macOS.
     */
    var enableSandboxing: Boolean = false

    val linux: LinuxPlatformSettings = objects.newInstance(LinuxPlatformSettings::class.java)

    open fun linux(fn: Action<LinuxPlatformSettings>) {
        fn.execute(linux)
    }

    val macOS: JvmMacOSPlatformSettings = objects.newInstance(JvmMacOSPlatformSettings::class.java)

    open fun macOS(fn: Action<JvmMacOSPlatformSettings>) {
        fn.execute(macOS)
    }

    val windows: WindowsPlatformSettings = objects.newInstance(WindowsPlatformSettings::class.java)

    fun windows(fn: Action<WindowsPlatformSettings>) {
        fn.execute(windows)
    }

    @JvmOverloads
    fun fileAssociation(
        mimeType: String,
        extension: String,
        description: String,
        linuxIconFile: File? = null,
        windowsIconFile: File? = null,
        macOSIconFile: File? = null,
    ) {
        linux.fileAssociation(mimeType, extension, description, linuxIconFile)
        windows.fileAssociation(mimeType, extension, description, windowsIconFile)
        macOS.fileAssociation(mimeType, extension, description, macOSIconFile)
    }

    // --- Publishing ---

    val publish: PublishSettings = objects.newInstance(PublishSettings::class.java)

    fun publish(fn: Action<PublishSettings>) {
        fn.execute(publish)
    }

    // --- Compression level for archive formats ---

    var compressionLevel: CompressionLevel? = null

    // --- Artifact name template (e.g., "\${name}-\${version}-\${arch}.\${ext}") ---

    var artifactName: String? = null

    // --- URL protocol handlers (deep linking) ---

    val protocols: MutableList<UrlProtocol> = mutableListOf()

    fun protocol(
        name: String,
        vararg schemes: String,
    ) {
        protocols.add(UrlProtocol(name, schemes.toList()))
    }
}

data class UrlProtocol(
    val name: String,
    val schemes: List<String>,
)
