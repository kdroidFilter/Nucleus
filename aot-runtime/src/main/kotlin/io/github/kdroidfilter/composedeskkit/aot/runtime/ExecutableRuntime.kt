package io.github.kdroidfilter.composedeskkit.aot.runtime

public enum class ExecutableType {
    EXE,
    MSI,
    DMG,
    PKG,
    MSIX,
    DEB,
    RPM,
    DEV,
}

@Suppress("TooManyFunctions")
public object ExecutableRuntime {
    public const val TYPE_PROPERTY: String = "composedeskkit.executable.type"

    @JvmStatic
    public fun type(): ExecutableType = parseType(System.getProperty(TYPE_PROPERTY))

    @JvmStatic
    public fun type(propertyName: String): ExecutableType = parseType(System.getProperty(propertyName))

    @JvmStatic
    public fun isExe(): Boolean = type() == ExecutableType.EXE

    @JvmStatic
    public fun isMsi(): Boolean = type() == ExecutableType.MSI

    @JvmStatic
    public fun isDmg(): Boolean = type() == ExecutableType.DMG

    @JvmStatic
    public fun isPkg(): Boolean = type() == ExecutableType.PKG

    @JvmStatic
    public fun isMsix(): Boolean = type() == ExecutableType.MSIX

    @JvmStatic
    public fun isDeb(): Boolean = type() == ExecutableType.DEB

    @JvmStatic
    public fun isRpm(): Boolean = type() == ExecutableType.RPM

    @JvmStatic
    public fun isDev(): Boolean = type() == ExecutableType.DEV

    public fun parseType(rawValue: String?): ExecutableType =
        when (rawValue?.trim()?.lowercase()) {
            "exe", ".exe" -> ExecutableType.EXE
            "msi", ".msi" -> ExecutableType.MSI
            "dmg", ".dmg" -> ExecutableType.DMG
            "pkg", ".pkg" -> ExecutableType.PKG
            "msix", ".msix" -> ExecutableType.MSIX
            "deb", ".deb" -> ExecutableType.DEB
            "rpm", ".rpm" -> ExecutableType.RPM
            "dev", "development", "app-image", "appimage" -> ExecutableType.DEV
            else -> ExecutableType.DEV
        }
}
