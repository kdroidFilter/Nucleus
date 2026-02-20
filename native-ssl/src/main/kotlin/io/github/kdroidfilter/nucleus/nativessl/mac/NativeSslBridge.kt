package io.github.kdroidfilter.nucleus.nativessl.mac

import io.github.kdroidfilter.nucleus.nativessl.debugln
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "NativeSslBridge"

internal object NativeSslBridge {
    private val logger = Logger.getLogger(NativeSslBridge::class.java.simpleName)

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        try {
            System.loadLibrary("nucleus_ssl")
            loaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to JAR extraction
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            val arch =
                System.getProperty("os.arch").let {
                    if (it == "aarch64" || it == "arm64") "aarch64" else "x64"
                }
            val resourcePath = "/nucleus/native/darwin-$arch/libnucleus_ssl.dylib"
            val stream =
                NativeSslBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-native")
            val tempLib = tempDir.resolve("libnucleus_ssl.dylib")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_ssl native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    @JvmStatic
    external fun nativeGetSystemCertificates(): Array<ByteArray>

    fun getSystemCertificates(): List<ByteArray> {
        if (!loaded) return emptyList()
        return try {
            nativeGetSystemCertificates().toList().also {
                debugln(TAG) { "Loaded ${it.size} certificates from macOS Security framework" }
            }
        } catch (e: UnsatisfiedLinkError) {
            logger.log(Level.WARNING, "JNI call failed for nativeGetSystemCertificates", e)
            emptyList()
        }
    }
}
