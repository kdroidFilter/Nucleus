package io.github.kdroidfilter.nucleus.graalvm

import io.github.kdroidfilter.nucleus.hidpi.getLinuxNativeScaleFactor
import java.io.File
import java.nio.charset.Charset

object GraalVmInitializer {
    val isNativeImage: Boolean =
        System.getProperty("org.graalvm.nativeimage.imagecode") != null

    /** Call once at the very start of main(), before any AWT/Compose usage. */
    fun initialize() {
        if (isNativeImage) {
            // Metal L&F — avoids platform-specific modules unsupported in native image
            System.setProperty("swing.defaultlaf", "javax.swing.plaf.metal.MetalLookAndFeel")

            // java.home → executable dir so Skiko finds jawt
            val execDir =
                File(
                    ProcessHandle
                        .current()
                        .info()
                        .command()
                        .orElse(""),
                ).parentFile?.absolutePath ?: "."
            System.setProperty("java.home", execDir)

            // java.library.path → execDir + execDir/bin
            val sep = File.pathSeparator
            System.setProperty("java.library.path", "$execDir$sep$execDir${File.separator}bin")

            // Early charset + fontmanager init
            Charset.defaultCharset()
            try {
                System.loadLibrary("fontmanager")
            } catch (_: Throwable) {
                // Ignore — fontmanager may already be loaded or unavailable
            }
        }

        // Linux HiDPI (applies to both JVM and native image)
        if (System.getProperty("sun.java2d.uiScale") == null) {
            val scale = getLinuxNativeScaleFactor()
            if (scale > 0.0) System.setProperty("sun.java2d.uiScale", scale.toString())
        }
    }
}
