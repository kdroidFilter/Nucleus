/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.internal.electronbuilder

import io.github.kdroidfilter.nucleus.desktop.application.dsl.AppXSettings
import io.github.kdroidfilter.nucleus.desktop.application.dsl.FileAssociation
import io.github.kdroidfilter.nucleus.desktop.application.dsl.FlatpakSettings
import io.github.kdroidfilter.nucleus.desktop.application.dsl.JvmApplicationDistributions
import io.github.kdroidfilter.nucleus.desktop.application.dsl.NsisSettings
import io.github.kdroidfilter.nucleus.desktop.application.dsl.PublishSettings
import io.github.kdroidfilter.nucleus.desktop.application.dsl.SnapSettings
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat
import io.github.kdroidfilter.nucleus.internal.utils.OS
import io.github.kdroidfilter.nucleus.internal.utils.currentOS
import java.io.File

/**
 * Generates an electron-builder YAML configuration from the Gradle DSL settings.
 *
 * Maps ComposeDeskKit DSL properties to the electron-builder configuration schema,
 * producing a `electron-builder.yml` file consumed by `electron-builder --prepackaged`.
 */
@Suppress("TooManyFunctions")
internal class ElectronBuilderConfigGenerator {
    /**
     * Generates the electron-builder config YAML content.
     *
     * @param distributions The JVM application distribution settings from the DSL.
     * @param targetFormat The specific target format being built.
     * @param appImageDir The prepackaged app-image directory from jpackage.
     * @return The YAML configuration content as a string.
     */
    fun generateConfig(
        distributions: JvmApplicationDistributions,
        targetFormat: TargetFormat,
        appImageDir: File,
        startupWMClass: String? = null,
        linuxIconOverride: File? = null,
    ): String {
        val yaml = StringBuilder()

        // --- Common settings ---
        appendIfNotNull(yaml, "productName", distributions.packageName)
        appendIfNotNull(yaml, "appId", distributions.packageName?.let { "com.app.$it" })
        appendIfNotNull(yaml, "copyright", distributions.copyright)

        if (distributions.homepage != null) {
            yaml.appendLine("extraMetadata:")
            yaml.appendLine("  homepage: ${distributions.homepage}")
        }

        yaml.appendLine("directories:")
        yaml.appendLine("  output: .")

        appendIfNotNull(yaml, "compression", distributions.compressionLevel)
        appendIfNotNull(yaml, "artifactName", distributions.artifactName)
        generateFileAssociations(yaml, distributions, targetFormat)

        // --- Platform-specific config ---
        when (currentOS) {
            OS.MacOS -> generateMacConfig(yaml, distributions, targetFormat)
            OS.Windows -> generateWindowsConfig(yaml, distributions, targetFormat)
            OS.Linux -> generateLinuxConfig(yaml, distributions, targetFormat, startupWMClass, linuxIconOverride)
        }

        // --- Protocols ---
        if (distributions.protocols.isNotEmpty()) {
            yaml.appendLine("protocols:")
            for (protocol in distributions.protocols) {
                yaml.appendLine("  - name: \"${protocol.name}\"")
                yaml.appendLine("    schemes:")
                for (scheme in protocol.schemes) {
                    yaml.appendLine("      - \"$scheme\"")
                }
            }
        }

        // --- Publishing ---
        generatePublishConfig(yaml, distributions.publish)

        return yaml.toString()
    }

    private fun generateMacConfig(
        yaml: StringBuilder,
        distributions: JvmApplicationDistributions,
        targetFormat: TargetFormat,
    ) {
        yaml.appendLine("mac:")
        yaml.appendLine("  target:")
        yaml.appendLine("    - target: ${targetFormat.id}")
        appendIfNotNull(yaml, "  category", distributions.macOS.appCategory)
        appendIfNotNull(
            yaml,
            "  icon",
            distributions.macOS.iconFile.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(yaml, "  minimumSystemVersion", distributions.macOS.minimumSystemVersion)

        when (targetFormat) {
            TargetFormat.Dmg -> {
                yaml.appendLine("dmg:")
                yaml.appendLine("  sign: false")
            }
            TargetFormat.Pkg -> {
                yaml.appendLine("pkg:")
                appendIfNotNull(yaml, "  installLocation", distributions.macOS.installationPath)
            }
            else -> {}
        }
    }

    private fun generateWindowsConfig(
        yaml: StringBuilder,
        distributions: JvmApplicationDistributions,
        targetFormat: TargetFormat,
    ) {
        yaml.appendLine("win:")
        yaml.appendLine("  target:")
        yaml.appendLine("    - target: ${targetFormat.electronBuilderTarget}")
        appendIfNotNull(
            yaml,
            "  icon",
            distributions.windows.iconFile.orNull
                ?.asFile
                ?.absolutePath,
        )

        generateWindowsSigningConfig(yaml, distributions)

        when (targetFormat) {
            TargetFormat.Nsis, TargetFormat.Exe -> {
                yaml.appendLine("nsis:")
                generateNsisSettings(yaml, distributions.windows.nsis, "  ")
            }
            TargetFormat.NsisWeb -> {
                yaml.appendLine("nsisWeb:")
                generateNsisSettings(yaml, distributions.windows.nsis, "  ")
            }
            TargetFormat.Msi -> {
                yaml.appendLine("msi:")
                appendIfNotNull(yaml, "  upgradeCode", distributions.windows.upgradeUuid)
            }
            TargetFormat.AppX -> generateAppXConfig(yaml, distributions.windows.appx)
            TargetFormat.Portable -> yaml.appendLine("portable: {}")
            else -> {}
        }
    }

    private fun generateWindowsSigningConfig(
        yaml: StringBuilder,
        distributions: JvmApplicationDistributions,
    ) {
        val signing = distributions.windows.signing
        if (!signing.enabled) return

        yaml.appendLine("  signtoolOptions:")
        appendIfNotNull(
            yaml,
            "    certificateFile",
            signing.certificateFile.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(yaml, "    certificatePassword", signing.certificatePassword)
        appendIfNotNull(yaml, "    certificateSha1", signing.certificateSha1)
        appendIfNotNull(yaml, "    certificateSubjectName", signing.certificateSubjectName)
        appendIfNotNull(yaml, "    rfc3161TimeStampServer", signing.timestampServer)

        if (signing.azureTenantId != null) {
            yaml.appendLine("  azureSignOptions:")
            appendIfNotNull(yaml, "    endpoint", signing.azureEndpoint)
            appendIfNotNull(yaml, "    certificateProfileName", signing.azureCertificateProfileName)
            appendIfNotNull(yaml, "    codeSigningAccountName", signing.azureCodeSigningAccountName)
        }
    }

    private fun generateFileAssociations(
        yaml: StringBuilder,
        distributions: JvmApplicationDistributions,
        targetFormat: TargetFormat,
    ) {
        val associations =
            when (targetFormat) {
                TargetFormat.Exe,
                TargetFormat.Nsis,
                TargetFormat.NsisWeb,
                TargetFormat.Msi,
                TargetFormat.AppX,
                -> distributions.windows.fileAssociations
                else -> emptySet()
            }
        if (associations.isEmpty()) return

        yaml.appendLine("fileAssociations:")
        for (association in associations) {
            appendFileAssociation(yaml, association)
        }
    }

    private fun appendFileAssociation(
        yaml: StringBuilder,
        association: FileAssociation,
    ) {
        val normalizedExtension = association.extension.trim().removePrefix(".")
        if (normalizedExtension.isBlank()) return

        yaml.appendLine("  - ext: \"$normalizedExtension\"")
        appendIfNotNull(yaml, "    name", association.description)
        appendIfNotNull(yaml, "    description", association.description)
        appendIfNotNull(yaml, "    icon", association.iconFile?.absolutePath)
    }

    private fun generateNsisSettings(
        yaml: StringBuilder,
        nsis: NsisSettings,
        indent: String,
    ) {
        yaml.appendLine("${indent}oneClick: ${nsis.oneClick}")
        yaml.appendLine("${indent}allowElevation: ${nsis.allowElevation}")
        yaml.appendLine("${indent}perMachine: ${nsis.perMachine}")
        yaml.appendLine("${indent}allowToChangeInstallationDirectory: ${nsis.allowToChangeInstallationDirectory}")
        yaml.appendLine("${indent}createDesktopShortcut: ${nsis.createDesktopShortcut}")
        yaml.appendLine("${indent}createStartMenuShortcut: ${nsis.createStartMenuShortcut}")
        yaml.appendLine("${indent}runAfterFinish: ${nsis.runAfterFinish}")
        yaml.appendLine("${indent}deleteAppDataOnUninstall: ${nsis.deleteAppDataOnUninstall}")

        appendNsisFileSettings(yaml, nsis, indent)

        if (nsis.multiLanguageInstaller) {
            yaml.appendLine("${indent}multiLanguageInstaller: true")
        }
        if (nsis.installerLanguages.isNotEmpty()) {
            yaml.appendLine("${indent}installerLanguages:")
            for (lang in nsis.installerLanguages) {
                yaml.appendLine("$indent  - \"$lang\"")
            }
        }
    }

    private fun appendNsisFileSettings(
        yaml: StringBuilder,
        nsis: NsisSettings,
        indent: String,
    ) {
        appendIfNotNull(
            yaml,
            "${indent}installerIcon",
            nsis.installerIcon.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(
            yaml,
            "${indent}uninstallerIcon",
            nsis.uninstallerIcon.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(
            yaml,
            "${indent}license",
            nsis.license.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(
            yaml,
            "${indent}include",
            nsis.includeScript.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(
            yaml,
            "${indent}script",
            nsis.script.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(
            yaml,
            "${indent}installerHeader",
            nsis.installerHeader.orNull
                ?.asFile
                ?.absolutePath,
        )
        appendIfNotNull(
            yaml,
            "${indent}installerSidebar",
            nsis.installerSidebar.orNull
                ?.asFile
                ?.absolutePath,
        )
    }

    private fun generateAppXConfig(
        yaml: StringBuilder,
        appx: AppXSettings,
    ) {
        yaml.appendLine("appx:")
        appendIfNotNull(yaml, "  applicationId", appx.applicationId)
        appendIfNotNull(yaml, "  displayName", appx.displayName)
        appendIfNotNull(yaml, "  identityName", appx.identityName)
        appendIfNotNull(yaml, "  publisher", appx.publisher)
        appendIfNotNull(yaml, "  publisherDisplayName", appx.publisherDisplayName)
        appx.languages?.let { languages ->
            yaml.appendLine("  languages:")
            for (lang in languages) {
                yaml.appendLine("    - \"$lang\"")
            }
        }
        if (appx.addAutoLaunchExtension) {
            yaml.appendLine("  addAutoLaunchExtension: true")
        }
    }

    private fun generateLinuxConfig(
        yaml: StringBuilder,
        distributions: JvmApplicationDistributions,
        targetFormat: TargetFormat,
        startupWMClass: String?,
        linuxIconOverride: File?,
    ) {
        yaml.appendLine("linux:")
        yaml.appendLine("  target:")
        yaml.appendLine("    - target: ${targetFormat.electronBuilderTarget}")
        val linuxIcon = linuxIconOverride ?: distributions.linux.iconFile.orNull?.asFile
        appendIfNotNull(
            yaml,
            "  icon",
            linuxIcon?.absolutePath,
        )
        appendIfNotNull(yaml, "  category", distributions.linux.appCategory)
        appendIfNotNull(yaml, "  maintainer", distributions.linux.debMaintainer)
        appendIfNotNull(yaml, "  vendor", distributions.vendor)
        appendIfNotNull(yaml, "  description", distributions.description)
        appendLinuxDesktopEntryConfig(yaml, distributions, startupWMClass)

        when (targetFormat) {
            TargetFormat.Deb -> {
                yaml.appendLine("deb:")
                if (distributions.linux.debDepends.isNotEmpty()) {
                    yaml.appendLine("  depends:")
                    for (dep in distributions.linux.debDepends) {
                        yaml.appendLine("    - \"$dep\"")
                    }
                }
            }
            TargetFormat.Rpm -> {
                yaml.appendLine("rpm:")
                if (distributions.linux.rpmRequires.isNotEmpty()) {
                    yaml.appendLine("  depends:")
                    for (dep in distributions.linux.rpmRequires) {
                        yaml.appendLine("    - \"$dep\"")
                    }
                }
            }
            TargetFormat.Snap -> generateSnapConfig(yaml, distributions.linux.snap)
            TargetFormat.Flatpak -> generateFlatpakConfig(yaml, distributions.linux.flatpak)
            else -> {}
        }
    }

    private fun appendLinuxDesktopEntryConfig(
        yaml: StringBuilder,
        distributions: JvmApplicationDistributions,
        startupWMClass: String?,
    ) {
        val entryOverrides = linkedMapOf<String, String>()
        entryOverrides.putAll(distributions.linux.appImage.desktopEntries)
        val hasStartupWMClassOverride =
            entryOverrides.keys.any { it.equals("StartupWMClass", ignoreCase = true) }
        if (!hasStartupWMClassOverride) {
            startupWMClass?.takeIf { it.isNotBlank() }?.let {
                entryOverrides["StartupWMClass"] = it
            }
        }
        if (entryOverrides.isEmpty()) return

        yaml.appendLine("  desktop:")
        yaml.appendLine("    entry:")
        for ((key, value) in entryOverrides) {
            yaml.appendLine("      \"${key.escapeForYamlDoubleQuotes()}\": \"${value.escapeForYamlDoubleQuotes()}\"")
        }
    }

    private fun generateSnapConfig(
        yaml: StringBuilder,
        snap: SnapSettings,
    ) {
        yaml.appendLine("snap:")
        yaml.appendLine("  confinement: ${snap.confinement}")
        yaml.appendLine("  grade: ${snap.grade}")
        appendIfNotNull(yaml, "  summary", snap.summary)
        appendIfNotNull(yaml, "  base", snap.base)
        if (snap.autoStart) {
            yaml.appendLine("  autoStart: true")
        }
        appendIfNotNull(yaml, "  compression", snap.compression)
        if (snap.plugs.isNotEmpty()) {
            yaml.appendLine("  plugs:")
            for (plug in snap.plugs) {
                yaml.appendLine("    - \"$plug\"")
            }
        }
    }

    private fun generateFlatpakConfig(
        yaml: StringBuilder,
        flatpak: FlatpakSettings,
    ) {
        yaml.appendLine("flatpak:")
        yaml.appendLine("  runtime: ${flatpak.runtime}")
        yaml.appendLine("  runtimeVersion: \"${flatpak.runtimeVersion}\"")
        yaml.appendLine("  sdk: ${flatpak.sdk}")
        yaml.appendLine("  branch: ${flatpak.branch}")
        appendIfNotNull(
            yaml,
            "  license",
            flatpak.license.orNull
                ?.asFile
                ?.absolutePath,
        )
        if (flatpak.finishArgs.isNotEmpty()) {
            yaml.appendLine("  finishArgs:")
            for (arg in flatpak.finishArgs) {
                yaml.appendLine("    - \"$arg\"")
            }
        }
    }

    private fun generatePublishConfig(
        yaml: StringBuilder,
        publish: PublishSettings,
    ) {
        val github = publish.github
        val s3 = publish.s3

        if (!github.enabled && !s3.enabled) return

        yaml.appendLine("publish:")
        if (github.enabled) {
            yaml.appendLine("  - provider: github")
            appendIfNotNull(yaml, "    owner", github.owner)
            appendIfNotNull(yaml, "    repo", github.repo)
            appendIfNotNull(yaml, "    token", github.token)
            yaml.appendLine("    channel: ${github.channel}")
            yaml.appendLine("    releaseType: ${github.releaseType}")
        }
        if (s3.enabled) {
            yaml.appendLine("  - provider: s3")
            appendIfNotNull(yaml, "    bucket", s3.bucket)
            appendIfNotNull(yaml, "    region", s3.region)
            appendIfNotNull(yaml, "    path", s3.path)
            appendIfNotNull(yaml, "    acl", s3.acl)
        }
    }

    private fun appendIfNotNull(
        yaml: StringBuilder,
        key: String,
        value: String?,
    ) {
        if (value != null) {
            yaml.appendLine("$key: \"${value.escapeForYamlDoubleQuotes()}\"")
        }
    }

    private fun String.escapeForYamlDoubleQuotes(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
