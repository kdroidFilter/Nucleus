// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package jewelsample.showcase.views

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.skiko.hostOs

class KeyBinding(
    val macOs: Set<String> = emptySet(),
    val windows: Set<String> = emptySet(),
    val linux: Set<String> = emptySet(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyBinding

        if (macOs != other.macOs) return false
        if (windows != other.windows) return false
        if (linux != other.linux) return false

        return true
    }

    override fun hashCode(): Int {
        var result = macOs.hashCode()
        result = 31 * result + windows.hashCode()
        result = 31 * result + linux.hashCode()
        return result
    }

    override fun toString(): String = "KeyBinding(macOs=$macOs, windows=$windows, linux=$linux)"

    companion object
}

fun KeyBinding.forCurrentOs(): Set<String> =
    when {
        hostOs.isMacOS -> macOs
        hostOs.isLinux -> linux
        else -> windows
    }

class ViewInfo(
    val title: String,
    val iconKey: IconKey,
    val keyboardShortcut: KeyBinding? = null,
    val content: @Composable () -> Unit,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ViewInfo

        if (title != other.title) return false
        if (iconKey != other.iconKey) return false
        if (keyboardShortcut != other.keyboardShortcut) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + iconKey.hashCode()
        result = 31 * result + (keyboardShortcut?.hashCode() ?: 0)
        result = 31 * result + content.hashCode()
        return result
    }

    override fun toString(): String =
        "ViewInfo(" +
            "title='$title', " +
            "iconKey=$iconKey, " +
            "keyboardShortcut=$keyboardShortcut, " +
            "content=$content" +
            ")"

    companion object
}
