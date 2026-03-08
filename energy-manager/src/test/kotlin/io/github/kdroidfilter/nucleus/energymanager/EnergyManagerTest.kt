package io.github.kdroidfilter.nucleus.energymanager

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that thread-level efficiency mode actually changes OS scheduling
 * parameters by reading /proc/thread-self/stat (nice) and ionice (ioprio).
 *
 * These tests only run on Linux — they are skipped on other platforms.
 */
class EnergyManagerTest {

    private fun assumeLinux() {
        assumeTrue("Test requires Linux", System.getProperty("os.name").lowercase().contains("linux"))
    }

    @Test
    fun `thread efficiency mode changes nice and ioprio on dedicated thread`() {
        assumeLinux()
        assertTrue(EnergyManager.isAvailable(), "Energy manager not available")

        var niceBefore = 0
        var niceAfter = 0
        var ioBefore = ""
        var ioAfter = ""
        var enableResult = EnergyManager.Result(false)

        val thread = Thread {
            val tid = readTid()
            niceBefore = readNice()
            ioBefore = readIoClass(tid)

            enableResult = EnergyManager.enableThreadEfficiencyMode()

            niceAfter = readNice()
            ioAfter = readIoClass(tid)
        }
        thread.start()
        thread.join()

        println("Enable result: $enableResult")
        println("Nice:     $niceBefore -> $niceAfter")
        println("IO class: $ioBefore -> $ioAfter")

        assertTrue(enableResult.success, "Enable failed: ${enableResult.message}")
        assertEquals(0, niceBefore, "Expected initial nice = 0")
        assertEquals(19, niceAfter, "Expected nice = 19 after enable")
        assertTrue(ioAfter.contains("idle", ignoreCase = true), "Expected IO idle, got: $ioAfter")
    }

    @Test
    fun `withEfficiencyMode applies settings inside block`() = runBlocking {
        assumeLinux()
        assertTrue(EnergyManager.isAvailable())

        val (nice, ioClass, value) = EnergyManager.withEfficiencyMode {
            val tid = readTid()
            Triple(readNice(), readIoClass(tid), 42)
        }

        println("Inside withEfficiencyMode: nice=$nice, ioClass=$ioClass")

        assertEquals(19, nice, "Expected nice = 19 inside withEfficiencyMode")
        assertTrue(ioClass.contains("idle", ignoreCase = true), "Expected IO idle, got: $ioClass")
        assertEquals(42, value)
    }

    @Test
    fun `thread efficiency mode does not affect other threads`() {
        assumeLinux()
        assertTrue(EnergyManager.isAvailable())

        var efficientNice = -1
        val thread = Thread {
            EnergyManager.enableThreadEfficiencyMode()
            efficientNice = readNice()
        }
        thread.start()
        thread.join()

        val mainNice = readNice()

        println("Efficient thread nice: $efficientNice")
        println("Main thread nice:      $mainNice")

        assertEquals(19, efficientNice)
        assertEquals(0, mainNice, "Main thread should not be affected")
    }

    companion object {
        /**
         * Reads the nice value of the calling thread via /proc/thread-self/stat.
         * Field layout after (comm): state ppid pgrp session tty_nr tpgid flags
         *   minflt cminflt majflt cmajflt utime stime cutime cstime priority nice ...
         * That's index 16 (0-based) after the ") " separator.
         */
        fun readNice(): Int {
            val stat = File("/proc/thread-self/stat").readText()
            val afterComm = stat.substringAfter(") ")
            return afterComm.split(" ")[16].toInt()
        }

        /** Reads the OS thread ID from /proc/thread-self/stat (first field). */
        fun readTid(): Long {
            val stat = File("/proc/thread-self/stat").readText()
            return stat.substringBefore(" ").toLong()
        }

        /** Reads I/O scheduling class via ionice for a given tid. */
        fun readIoClass(tid: Long): String {
            val process = ProcessBuilder("ionice", "-p", tid.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            return output
        }
    }
}
