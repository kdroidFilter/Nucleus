package io.github.kdroidfilter.composedeskkit.aot.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AotRuntimeTest {
    @Test
    fun `detects training when AOTCacheOutput flag is present`() {
        val mode = AotRuntime.detectMode(listOf("-Xmx512m", "-XX:AOTCacheOutput=/tmp/app.aot"))
        assertEquals(AotRuntimeMode.TRAINING, mode)
    }

    @Test
    fun `detects runtime when AOTCache flag is present`() {
        val mode = AotRuntime.detectMode(listOf("-Xmx512m", "-XX:AOTCache=/tmp/app.aot"))
        assertEquals(AotRuntimeMode.RUNTIME, mode)
    }

    @Test
    fun `training takes precedence when both flags are present`() {
        val mode =
            AotRuntime.detectMode(
                listOf(
                    "-XX:AOTCache=/tmp/app.aot",
                    "-XX:AOTCacheOutput=/tmp/app.aot",
                ),
            )
        assertEquals(AotRuntimeMode.TRAINING, mode)
    }

    @Test
    fun `returns off when no aot flags are present`() {
        val mode = AotRuntime.detectMode(listOf("-Xmx512m"))
        assertEquals(AotRuntimeMode.OFF, mode)
    }

    @Test
    fun `parses explicit mode property values`() {
        assertEquals(AotRuntimeMode.TRAINING, AotRuntime.parseModeProperty("train"))
        assertEquals(AotRuntimeMode.RUNTIME, AotRuntime.parseModeProperty("runtime"))
        assertEquals(AotRuntimeMode.OFF, AotRuntime.parseModeProperty("off"))
    }

    @Test
    fun `returns null for empty or unknown mode property`() {
        assertNull(AotRuntime.parseModeProperty(""))
        assertNull(AotRuntime.parseModeProperty("unknown"))
    }
}
