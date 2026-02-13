package com.example.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.kdroidfilter.composedeskkit.aot.runtime.AotRuntime

private const val AOT_TRAINING_DURATION_MS = 15_000L

fun main() {
    // Stop app after 15 seconds during AOT training mode
    // Use -Dcomposedeskkit.aot.mode=training to test
    if (AotRuntime.isTraining()) {
        println("[AOT] Training mode - will exit in 15 seconds")

        if (isHeadlessLinux()) {
            println("[AOT] Headless Linux detected (no DISPLAY/WAYLAND_DISPLAY). Skipping UI.")
            Thread.sleep(AOT_TRAINING_DURATION_MS)
            println("[AOT] Time's up, exiting...")
            kotlin.system.exitProcess(0)
        }

        Thread({
            Thread.sleep(AOT_TRAINING_DURATION_MS)
            println("[AOT] Time's up, exiting...")
            kotlin.system.exitProcess(0)
        }, "aot-timer").apply {
            isDaemon = false
            start()
        }
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ComposeDeskKit Demo",
        ) {
            app()
        }
    }
}

private fun isHeadlessLinux(): Boolean {
    val os = System.getProperty("os.name").lowercase()
    if (!os.contains("linux")) return false
    val display = System.getenv("DISPLAY")
    val wayland = System.getenv("WAYLAND_DISPLAY")
    return display.isNullOrBlank() && wayland.isNullOrBlank()
}

@Composable
fun app() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "ComposeDeskKit Demo",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(modifier = Modifier.height(24.dp))

                var count by remember { mutableStateOf(0) }
                Button(onClick = { count++ }) {
                    Text("Clicked $count times")
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "System Info",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                Text("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
                Text("Runtime: ${System.getProperty("java.runtime.name", "Unknown")}")
            }
        }
    }
}
