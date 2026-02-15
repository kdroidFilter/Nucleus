package io.github.kdroidfilter.nucleus.updater

import io.github.kdroidfilter.nucleus.updater.exception.ChecksumException
import io.github.kdroidfilter.nucleus.updater.exception.NetworkException
import io.github.kdroidfilter.nucleus.updater.exception.NoMatchingFileException
import io.github.kdroidfilter.nucleus.updater.exception.UpdateException
import io.github.kdroidfilter.nucleus.updater.internal.ChecksumVerifier
import io.github.kdroidfilter.nucleus.updater.internal.FileSelector
import io.github.kdroidfilter.nucleus.updater.internal.PlatformInfo
import io.github.kdroidfilter.nucleus.updater.internal.PlatformInstaller
import io.github.kdroidfilter.nucleus.updater.internal.YamlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class NucleusUpdater(
    private val config: UpdaterConfig,
) {
    val currentVersion: String get() = config.currentVersion

    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    suspend fun checkForUpdates(): UpdateResult {
        if (config.isDevMode()) return UpdateResult.NotAvailable
        return try {
            doCheckForUpdates()
        } catch (e: UpdateException) {
            UpdateResult.Error(e)
        } catch (e: Exception) {
            UpdateResult.Error(NetworkException("Failed to check for updates", e))
        }
    }

    fun downloadUpdate(info: UpdateInfo): Flow<DownloadProgress> =
        flow {
            val targetFile = info.currentFile
            val tempDir = System.getProperty("java.io.tmpdir")
            val tempFile = File(tempDir, "${targetFile.fileName}.download")
            val finalFile = File(tempDir, targetFile.fileName)

            try {
                val requestBuilder =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create(targetFile.url))
                        .GET()
                applyAuthHeaders(requestBuilder)
                val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())

                if (response.statusCode() != 200) {
                    throw NetworkException("HTTP ${response.statusCode()} downloading ${targetFile.url}")
                }

                val totalBytes = targetFile.size
                var bytesDownloaded = 0L

                response.body().use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            val percent =
                                if (totalBytes > 0) {
                                    (bytesDownloaded.toDouble() / totalBytes * PERCENT_MAX).coerceAtMost(PERCENT_MAX)
                                } else {
                                    0.0
                                }
                            emit(DownloadProgress(bytesDownloaded, totalBytes, percent))
                        }
                    }
                }

                // Verify checksum
                if (!ChecksumVerifier.verify(tempFile, targetFile.sha512)) {
                    val actual = ChecksumVerifier.computeSha512Base64(tempFile)
                    tempFile.delete()
                    throw ChecksumException(targetFile.sha512, actual)
                }

                // Rename to final file
                if (finalFile.exists()) finalFile.delete()
                tempFile.renameTo(finalFile)

                emit(DownloadProgress(bytesDownloaded, totalBytes, PERCENT_MAX, finalFile))
            } catch (e: UpdateException) {
                tempFile.delete()
                throw e
            } catch (e: Exception) {
                tempFile.delete()
                throw NetworkException("Download failed", e)
            }
        }.flowOn(Dispatchers.IO)

    fun quitAndInstall(installerFile: File) {
        val platform = PlatformInfo.currentPlatform()
        PlatformInstaller.install(installerFile, platform)
    }

    private fun doCheckForUpdates(): UpdateResult {
        val platform = PlatformInfo.currentPlatform()
        val arch = PlatformInfo.currentArch()
        val metadataUrl = config.provider.getUpdateMetadataUrl(config.channel, platform)

        val requestBuilder =
            HttpRequest
                .newBuilder()
                .uri(URI.create(metadataUrl))
                .GET()
        applyAuthHeaders(requestBuilder)
        val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            return UpdateResult.Error(NetworkException("HTTP ${response.statusCode()} for $metadataUrl"))
        }

        val metadata = YamlParser.parse(response.body())
        val currentVersion = Version.fromString(config.currentVersion)
        val remoteVersion = Version.fromString(metadata.version)

        val isNewer = remoteVersion > currentVersion
        val isDowngrade = remoteVersion < currentVersion

        if (!isNewer && !(config.allowDowngrade && isDowngrade)) {
            return UpdateResult.NotAvailable
        }

        // Skip pre-release remote unless allowed
        if (remoteVersion.meta.isNotEmpty() && !config.resolvedAllowPrerelease()) {
            return UpdateResult.NotAvailable
        }

        val format =
            config.executableType
                ?: System.getProperty("nucleus.executable.type")

        val selectedFile =
            FileSelector.select(
                files = metadata.files,
                platform = platform,
                arch = arch,
                format = format,
            ) ?: return UpdateResult.Error(
                NoMatchingFileException(
                    platform.name,
                    arch.name,
                    format ?: "auto",
                ),
            )

        val updateInfo =
            UpdateInfo(
                version = metadata.version,
                releaseDate = metadata.releaseDate,
                files =
                    metadata.files.map { file ->
                        UpdateFile(
                            url = config.provider.getDownloadUrl(file.url, metadata.version),
                            sha512 = file.sha512,
                            size = file.size,
                            blockMapSize = file.blockMapSize,
                            fileName = file.url,
                        )
                    },
                currentFile =
                    UpdateFile(
                        url = config.provider.getDownloadUrl(selectedFile.url, metadata.version),
                        sha512 = selectedFile.sha512,
                        size = selectedFile.size,
                        blockMapSize = selectedFile.blockMapSize,
                        fileName = selectedFile.url,
                    ),
            )

        return UpdateResult.Available(updateInfo)
    }

    private fun applyAuthHeaders(builder: HttpRequest.Builder) {
        config.provider.authHeaders().forEach { (key, value) ->
            builder.header(key, value)
        }
    }

    companion object {
        private const val PERCENT_MAX = 100.0
    }
}
