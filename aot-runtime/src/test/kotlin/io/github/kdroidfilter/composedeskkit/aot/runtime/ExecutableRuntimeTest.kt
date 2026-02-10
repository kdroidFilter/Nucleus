package io.github.kdroidfilter.composedeskkit.aot.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutableRuntimeTest {
    @Test
    fun `parses known executable types`() {
        assertEquals(ExecutableType.EXE, ExecutableRuntime.parseType("exe"))
        assertEquals(ExecutableType.MSI, ExecutableRuntime.parseType("msi"))
        assertEquals(ExecutableType.DMG, ExecutableRuntime.parseType("dmg"))
        assertEquals(ExecutableType.PKG, ExecutableRuntime.parseType("pkg"))
        assertEquals(ExecutableType.MSIX, ExecutableRuntime.parseType("msix"))
    }

    @Test
    fun `parses type variants`() {
        assertEquals(ExecutableType.EXE, ExecutableRuntime.parseType(".EXE"))
        assertEquals(ExecutableType.MSIX, ExecutableRuntime.parseType(" MsIx "))
        assertEquals(ExecutableType.DEV, ExecutableRuntime.parseType("app-image"))
    }

    @Test
    fun `returns dev for empty or unknown values`() {
        assertEquals(ExecutableType.DEV, ExecutableRuntime.parseType(null))
        assertEquals(ExecutableType.DEV, ExecutableRuntime.parseType(""))
        assertEquals(ExecutableType.DEV, ExecutableRuntime.parseType("unknown"))
    }

    @Test
    fun `reads type from custom system property`() {
        val propertyName = "composedeskkit.test.executable.type"
        val previousValue = System.getProperty(propertyName)
        try {
            System.setProperty(propertyName, "msix")
            assertEquals(ExecutableType.MSIX, ExecutableRuntime.type(propertyName))
        } finally {
            restoreSystemProperty(propertyName, previousValue)
        }
    }

    @Test
    fun `boolean helpers expose target type and dev fallback`() {
        val previousValue = System.getProperty(ExecutableRuntime.TYPE_PROPERTY)
        try {
            System.setProperty(ExecutableRuntime.TYPE_PROPERTY, "msi")
            assertTrue(ExecutableRuntime.isMsi())
            assertFalse(ExecutableRuntime.isDev())

            System.setProperty(ExecutableRuntime.TYPE_PROPERTY, "rpm")
            assertTrue(ExecutableRuntime.isRpm())
            assertFalse(ExecutableRuntime.isDeb())

            System.setProperty(ExecutableRuntime.TYPE_PROPERTY, "other")
            assertTrue(ExecutableRuntime.isDev())
            assertFalse(ExecutableRuntime.isMsi())
        } finally {
            restoreSystemProperty(ExecutableRuntime.TYPE_PROPERTY, previousValue)
        }
    }

    private fun restoreSystemProperty(
        name: String,
        value: String?,
    ) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }
}
