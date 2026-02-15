package io.github.kdroidfilter.nucleus.updater

import io.github.kdroidfilter.nucleus.updater.internal.Arch
import io.github.kdroidfilter.nucleus.updater.internal.FileSelector
import io.github.kdroidfilter.nucleus.updater.internal.YamlFileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileSelectorTest {
    private val linuxFiles =
        listOf(
            YamlFileEntry("App-1.0.0-linux-amd64.deb", "hash1", 100L, null),
            YamlFileEntry("App-1.0.0-linux-arm64.deb", "hash2", 200L, null),
            YamlFileEntry("App-1.0.0-linux-amd64.rpm", "hash3", 300L, null),
            YamlFileEntry("App-1.0.0-linux-arm64.rpm", "hash4", 400L, null),
        )

    private val macFiles =
        listOf(
            YamlFileEntry("App-1.0.0-mac-x64.dmg", "hash1", 100L, null),
            YamlFileEntry("App-1.0.0-mac-arm64.dmg", "hash2", 200L, null),
        )

    private val windowsFiles =
        listOf(
            YamlFileEntry("App-1.0.0-x64.exe", "hash1", 100L, null),
            YamlFileEntry("App-1.0.0-arm64.exe", "hash2", 200L, null),
            YamlFileEntry("App-1.0.0-x64.msi", "hash3", 300L, null),
        )

    @Test
    fun `select deb for linux x64`() {
        val result = FileSelector.select(linuxFiles, Platform.LINUX, Arch.X64, "deb")
        assertNotNull(result)
        assertEquals("App-1.0.0-linux-amd64.deb", result!!.url)
    }

    @Test
    fun `select deb for linux arm64`() {
        val result = FileSelector.select(linuxFiles, Platform.LINUX, Arch.ARM64, "deb")
        assertNotNull(result)
        assertEquals("App-1.0.0-linux-arm64.deb", result!!.url)
    }

    @Test
    fun `select rpm for linux x64`() {
        val result = FileSelector.select(linuxFiles, Platform.LINUX, Arch.X64, "rpm")
        assertNotNull(result)
        assertEquals("App-1.0.0-linux-amd64.rpm", result!!.url)
    }

    @Test
    fun `select dmg for mac arm64`() {
        val result = FileSelector.select(macFiles, Platform.MACOS, Arch.ARM64, "dmg")
        assertNotNull(result)
        assertEquals("App-1.0.0-mac-arm64.dmg", result!!.url)
    }

    @Test
    fun `select exe for windows x64`() {
        val result = FileSelector.select(windowsFiles, Platform.WINDOWS, Arch.X64, "exe")
        assertNotNull(result)
        assertEquals("App-1.0.0-x64.exe", result!!.url)
    }

    @Test
    fun `select msi for windows x64`() {
        val result = FileSelector.select(windowsFiles, Platform.WINDOWS, Arch.X64, "msi")
        assertNotNull(result)
        assertEquals("App-1.0.0-x64.msi", result!!.url)
    }

    @Test
    fun `auto-detect platform when format is null`() {
        val result = FileSelector.select(macFiles, Platform.MACOS, Arch.ARM64, null)
        assertNotNull(result)
        assertEquals("App-1.0.0-mac-arm64.dmg", result!!.url)
    }

    @Test
    fun `fallback to first match when no arch in filename`() {
        val files =
            listOf(
                YamlFileEntry("App-1.0.0.dmg", "hash1", 100L, null),
            )
        val result = FileSelector.select(files, Platform.MACOS, Arch.ARM64, "dmg")
        assertNotNull(result)
        assertEquals("App-1.0.0.dmg", result!!.url)
    }

    @Test
    fun `return null for empty file list`() {
        val result = FileSelector.select(emptyList(), Platform.LINUX, Arch.X64, "deb")
        assertNull(result)
    }

    @Test
    fun `return null when no matching format`() {
        val files =
            listOf(
                YamlFileEntry("App-1.0.0.deb", "hash1", 100L, null),
            )
        val result = FileSelector.select(files, Platform.LINUX, Arch.X64, "rpm")
        assertNull(result)
    }

    @Test
    fun `nsis format selects exe extension`() {
        val result = FileSelector.select(windowsFiles, Platform.WINDOWS, Arch.X64, "nsis")
        assertNotNull(result)
        assertEquals("App-1.0.0-x64.exe", result!!.url)
    }
}
