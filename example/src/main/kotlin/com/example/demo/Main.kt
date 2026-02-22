package com.example.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.demo.icons.MaterialIconsDark_mode
import com.example.demo.icons.MaterialIconsInfo
import com.example.demo.icons.MaterialIconsLight_mode
import com.example.demo.icons.VscodeCodiconsColorMode
import io.github.kdroidfilter.nucleus.aot.runtime.AotRuntime
import io.github.kdroidfilter.nucleus.core.runtime.DeepLinkHandler
import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.core.runtime.SingleInstanceManager
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.notification.builder.ExperimentalNotificationsApi
import io.github.kdroidfilter.nucleus.notification.builder.notification
import io.github.kdroidfilter.nucleus.notification.builder.sendNotification
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider
import io.github.kdroidfilter.nucleus.window.TitleBarScope
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedDialog
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialDialogTitleBar
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import io.github.kdroidfilter.nucleus.window.newFullscreenControls
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

private const val AOT_TRAINING_DURATION_MS = 45_000L

private val deepLinkUri = mutableStateOf<URI?>(null)

@Suppress("LongMethod")
fun main(args: Array<String>) {
    DeepLinkHandler.register(args) { uri ->
        deepLinkUri.value = uri
    }

    // Stop app after 15 seconds during AOT training mode
    // Use -Dnucleus.aot.mode=training to test
    if (AotRuntime.isTraining()) {
        println("[AOT] Training mode - will exit in 15 seconds")

        Thread({
            Thread.sleep(AOT_TRAINING_DURATION_MS)
            println("[AOT] Time's up, exiting...")
            exitProcess(0)
        }, "aot-timer").apply {
            isDaemon = false
            start()
        }
    }

    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        var restoreRequestCount by remember { mutableStateOf(0) }
        var themeMode by remember { mutableStateOf(ThemeMode.System) }
        var showInfoDialog by remember { mutableStateOf(false) }

        val isFirstInstance =
            remember {
                SingleInstanceManager.isSingleInstance(
                    onRestoreFileCreated = { DeepLinkHandler.writeUriTo(this) },
                    onRestoreRequest = {
                        DeepLinkHandler.readUriFrom(this)
                        isWindowVisible = true
                        restoreRequestCount++
                    },
                )
            }

        if (!isFirstInstance) {
            exitApplication()
            return@application
        }

        if (isWindowVisible) {
            val isDark =
                when (themeMode) {
                    ThemeMode.System -> isSystemInDarkMode()
                    ThemeMode.Dark -> true
                    ThemeMode.Light -> false
                }
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                MaterialDecoratedWindow(
                    state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center)),
                    onCloseRequest = ::exitApplication,
                    title = "Nucleus Demo",
                ) {
                    MaterialTitleBar(modifier = Modifier.newFullscreenControls()) { _ ->
                        val titleBarAlignment =
                            if (Platform.Current == Platform.MacOS) Alignment.End else Alignment.Start

                        TitleBarIconButton(
                            imageVector =
                                when (themeMode) {
                                    ThemeMode.System -> VscodeCodiconsColorMode
                                    ThemeMode.Dark -> MaterialIconsDark_mode
                                    ThemeMode.Light -> MaterialIconsLight_mode
                                },
                            contentDescription = "Toggle theme",
                            modifier = Modifier.align(titleBarAlignment),
                            onClick = { themeMode = themeMode.next() },
                        )
                        TitleBarIconButton(
                            imageVector = MaterialIconsInfo,
                            contentDescription = "System info",
                            modifier = Modifier.align(titleBarAlignment),
                            onClick = { showInfoDialog = true },
                        )
                        Text(
                            title,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    LaunchedEffect(restoreRequestCount) {
                        if (restoreRequestCount > 0) {
                            window.toFront()
                            window.requestFocus()
                        }
                    }
                    app()

                    if (showInfoDialog) {
                        MaterialDecoratedDialog(
                            onCloseRequest = { showInfoDialog = false },
                            state = DialogState(size = DpSize(400.dp, 250.dp)),
                            title = "System Info",
                        ) {
                            val background = MaterialTheme.colorScheme.surface
                            LaunchedEffect(window, background) {
                                window.background = java.awt.Color(background.toArgb())
                            }
                            MaterialDialogTitleBar { _ ->
                                Text(
                                    title,
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Surface(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text("OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                                    Text(
                                        "Java: ${System.getProperty("java.version")}" +
                                            " (${System.getProperty("java.vendor")})",
                                    )
                                    Text("Runtime: ${System.getProperty("java.runtime.name", "Unknown")}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun app() {
    val currentDeepLink by deepLinkUri
    val updater =
        remember {
            NucleusUpdater {
                provider = GitHubProvider(owner = "kdroidfilter", repo = "Nucleus")
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NucleusAtom(atomSize = 200.dp)

                Spacer(modifier = Modifier.height(24.dp))

                NotificationButton()

                if (currentDeepLink != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Deep Link",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentDeepLink.toString(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (updater.isUpdateSupported()) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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

@OptIn(ExperimentalNotificationsApi::class)
@Composable
private fun NotificationButton() {
    var notificationSent by remember { mutableStateOf(false) }

    Button(
        onClick = {
            notificationSent = true
            notification(
                title = "Nucleus Demo",
                message = "This is a test notification from Nucleus!",
                onActivated = { println("Notification activated!") },
                onDismissed = { reason -> println("Notification dismissed: $reason") },
                onFailed = { println("Notification failed!") }
            ) {
                button("Reply") { println("Reply button clicked!") }
                button("Dismiss") { println("Dismiss button clicked!") }
            }.send()
        }
    ) {
        Text(if (notificationSent) "Notification Sent!" else "Send Notification")
    }
}

private enum class ThemeMode {
    System,
    Dark,
    Light,
    ;

    fun next(): ThemeMode =
        when (this) {
            System -> Dark
            Dark -> Light
            Light -> System
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "DEPRECATION")
@Composable
private fun TitleBarScope.TitleBarIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(contentDescription) } },
            state = rememberTooltipState(),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .hoverable(hoverInteraction)
                        .background(
                            if (isHovered) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            } else {
                                Color.Transparent
                            },
                        ).clickable(
                            interactionSource = hoverInteraction,
                            indication = null,
                        ) { onClick() }
                        .padding(4.dp)
                        .size(16.dp),
            )
        }
    }
}
