package com.example.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.kdroidfilter.nucleus.aot.runtime.AotRuntime
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider
import java.io.File

private const val AOT_TRAINING_DURATION_MS = 15_000L
private const val APP_VERSION = "1.0.0"

fun main() {
    // Stop app after 15 seconds during AOT training mode
    // Use -Dnucleus.aot.mode=training to test
    if (AotRuntime.isTraining()) {
        println("[AOT] Training mode - will exit in 15 seconds")

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

@Composable
fun app() {
    val updater = remember {
        NucleusUpdater {
            currentVersion = APP_VERSION
            provider = GitHubProvider(owner = "kdroidfilter", repo = "ComposeDeskKit")
            channel = "latest"
        }
    }

    var updateStatus by remember { mutableStateOf("Checking for updates...") }
    var downloadProgress by remember { mutableStateOf(-1.0) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        when (val result = updater.checkForUpdates()) {
            is UpdateResult.Available -> {
                updateStatus = "Update available: v${result.info.version}"
                updater.downloadUpdate(result.info).collect { progress ->
                    downloadProgress = progress.percent
                    if (progress.file != null) {
                        downloadedFile = progress.file
                        updateStatus = "Download complete: v${result.info.version}"
                    }
                }
            }
            is UpdateResult.NotAvailable -> {
                updateStatus = "Up to date (v$APP_VERSION)"
            }
            is UpdateResult.Error -> {
                updateStatus = "Update check failed: ${result.exception.message}"
            }
        }
    }

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

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Auto-Update",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(updateStatus)

                if (downloadProgress in 0.0..99.9) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (downloadProgress / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                    Text("${downloadProgress.toInt()}%")
                }

                if (downloadedFile != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { updater.quitAndInstall(downloadedFile!!) }) {
                        Text("Install & Restart")
                    }
                }
            }
        }
    }
}
