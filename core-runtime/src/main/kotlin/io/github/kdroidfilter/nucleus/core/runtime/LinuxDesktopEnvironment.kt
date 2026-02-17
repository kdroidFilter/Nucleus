package io.github.kdroidfilter.nucleus.core.runtime

import java.util.Locale

enum class LinuxDesktopEnvironment {
    Gnome,
    KDE,
    XFCE,
    Unknown,
    ;

    companion object {
        val Current: LinuxDesktopEnvironment by lazy {
            val desktop = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase(Locale.ENGLISH) ?: ""
            val session = System.getenv("DESKTOP_SESSION")?.lowercase(Locale.ENGLISH) ?: ""
            when {
                desktop.contains("gnome") || session.contains("gnome") -> Gnome
                desktop.contains("kde") || session.contains("kde") || session.contains("plasma") -> KDE
                desktop.contains("xfce") || session.contains("xfce") -> XFCE
                else -> Unknown
            }
        }
    }
}
