package com.example.demo

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kdroid.composetray.tray.api.Tray
import io.github.kdroidfilter.nucleus.aot.runtime.AotRuntime
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider
import java.io.File

private const val AOT_TRAINING_DURATION_MS = 45_000L

fun main(args: Array<String>) {
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
//        Tray(
//            iconContent = {
//                Canvas(modifier = Modifier.fillMaxSize()) {
//                    // Important to use fillMaxSize()!
//                    drawCircle(
//                        color = Color.Red,
//                        radius = size.minDimension / 2,
//                        center = center,
//                    )
//                }
//            },
//            tooltip = "My Application",
//        ) {
//            Item("Quit") { exitApplication() }
//        } // Check Native lib

        val deepLinkUrl = args.firstOrNull { it.startsWith("nucleus://") }

        Window(
            state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center)),
            onCloseRequest = ::exitApplication,
            title = "Nucleus Demo",
        ) {
            app(deepLinkUrl)
        }
    }
}

@Composable
fun app(deepLinkUrl: String? = null) {
    val updater =
        remember {
            NucleusUpdater {
                provider = GitHubProvider(owner = "kdroidfilter", repo = "ComposeDeskKit")
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
                updateStatus = "Up to date (v${updater.currentVersion})"
            }
            is UpdateResult.Error -> {
                updateStatus = "Update check failed: ${result.exception.message}"
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NucleusAtom(atomSize = 200.dp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "System Info",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                Text("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
                Text("Runtime: ${System.getProperty("java.runtime.name", "Unknown")}")

                if (deepLinkUrl != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Deep Link",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(deepLinkUrl)
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (updater.isUpdateSupported()) {
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
                        Button(onClick = { updater.installAndRestart(downloadedFile!!) }) {
                            Text("Install & Restart")
                        }
                    }
                }
            }
        }
    }
}
