package com.liquidtv.app.data.audio

import android.content.Context
import android.util.Log
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.domain.model.ExternalAudioCatalogResult
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioSyncStage
import com.liquidtv.app.domain.repository.SonixControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext

interface ExternalAudioCatalogRepository {
    fun peekCached(): ExternalAudioCatalogResult? = null
    suspend fun preload(): ExternalAudioCatalogResult = loadCached()
    suspend fun loadCached(): ExternalAudioCatalogResult
    suspend fun syncFromPanel(forceRefresh: Boolean = false): ExternalAudioCatalogResult
    suspend fun syncFromPanelWithProgress(
        forceRefresh: Boolean = false,
        onProgress: (ExternalAudioSyncStage) -> Unit,
    ): ExternalAudioCatalogResult = syncFromPanel(forceRefresh)
}

sealed class AudioCatalogSyncException(
    val userMessageAr: String,
    cause: Throwable? = null,
) : IllegalStateException(userMessageAr, cause) {
    class DnsFailure(cause: Throwable?) :
        AudioCatalogSyncException("تعذّر العثور على عنوان خادم قائمة الصوت (خطأ DNS)", cause)

    class ConnectFailure(userMessageAr: String, cause: Throwable?) :
        AudioCatalogSyncException(userMessageAr, cause)

    class ReadTimeoutFailure(cause: Throwable?) :
        AudioCatalogSyncException("انتهت مهلة قراءة استجابة قائمة الصوت من الخادم", cause)

    class RedirectFailure(userMessageAr: String, cause: Throwable? = null) :
        AudioCatalogSyncException(userMessageAr, cause)

    class HttpStatusFailure(code: Int) :
        AudioCatalogSyncException("فشل جلب قائمة الصوتيات من الخادم (رمز $code)")

    class ParseFailure(userMessageAr: String, cause: Throwable? = null) :
        AudioCatalogSyncException(userMessageAr, cause)
}

@Serializable
private data class CachedAudioCatalogEnvelope(
    val version: Int = 2,
    val updatedAt: String = "",
    val lastSyncedEpochMs: Long = 0L,
    val sourceHash: String = "",
    val sourceLabel: String = "",
    val channels: List<ExternalAudioChannel> = emptyList(),
)

private data class CachedAudioCatalogState(
    val envelope: CachedAudioCatalogEnvelope,
    val result: ExternalAudioCatalogResult,
)

private data class InFlightSync(
    val forceRefresh: Boolean,
    val deferred: CompletableDeferred<ExternalAudioCatalogResult?>,
    val listeners: CopyOnWriteArrayList<(ExternalAudioSyncStage) -> Unit>,
)

@Singleton
class DefaultExternalAudioCatalogRepository internal constructor(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val okHttpClient: OkHttpClient,
    private val credentialCrypto: CredentialCrypto,
    private val sonixControlRepository: SonixControlRepository,
    private val dnsResolver: (String) -> Array<InetAddress>,
    private val connectionFactory: (URL) -> HttpURLConnection,
    private val m3uConnectTimeoutMs: Int,
    private val m3uReadTimeoutMs: Int,
    private val clockMs: () -> Long,
) : ExternalAudioCatalogRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        credentialCrypto: CredentialCrypto,
        sonixControlRepository: SonixControlRepository,
    ) : this(
        context = context,
        okHttpClient = okHttpClient,
        credentialCrypto = credentialCrypto,
        sonixControlRepository = sonixControlRepository,
        dnsResolver = { host -> InetAddress.getAllByName(host) },
        connectionFactory = { url -> url.openConnection() as HttpURLConnection },
        m3uConnectTimeoutMs = DEFAULT_M3U_CONNECT_TIMEOUT_MS,
        m3uReadTimeoutMs = DEFAULT_M3U_READ_TIMEOUT_MS,
        clockMs = { System.currentTimeMillis() },
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    private val cacheReadLock = Any()
    private val inFlightLock = Any()
    private val inMemoryCache = AtomicReference<CachedAudioCatalogState?>(null)

    @Volatile
    private var inFlightSync: InFlightSync? = null

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    override fun peekCached(): ExternalAudioCatalogResult? = inMemoryCache.get()?.result

    override suspend fun preload(): ExternalAudioCatalogResult = loadCached()

    override suspend fun loadCached(): ExternalAudioCatalogResult {
        inMemoryCache.get()?.let { return it.result }
        return withContext(Dispatchers.IO) {
            readCachedState()?.result ?: ExternalAudioCatalogResult(
                channels = emptyList(),
                fromCache = false,
                error = "لا توجد مصادر صوت محفوظة بعد",
            )
        }
    }

    override suspend fun syncFromPanel(forceRefresh: Boolean): ExternalAudioCatalogResult =
        syncFromPanelWithProgress(forceRefresh = forceRefresh) {}

    override suspend fun syncFromPanelWithProgress(
        forceRefresh: Boolean,
        onProgress: (ExternalAudioSyncStage) -> Unit,
    ): ExternalAudioCatalogResult {
        while (true) {
            coroutineContext.ensureActive()
            var isOwner = false
            var ownerSync: InFlightSync? = null
            var joinSync: InFlightSync? = null

            synchronized(inFlightLock) {
                val active = inFlightSync
                if (active != null) {
                    active.listeners.add(onProgress)
                    joinSync = active
                } else {
                    val created = InFlightSync(
                        forceRefresh = forceRefresh,
                        deferred = CompletableDeferred(),
                        listeners = CopyOnWriteArrayList<(ExternalAudioSyncStage) -> Unit>().apply {
                            add(onProgress)
                        },
                    )
                    inFlightSync = created
                    ownerSync = created
                    isOwner = true
                }
            }

            if (!isOwner) {
                val active = joinSync!!
                val sharedResult = active.deferred.await()
                if (sharedResult != null && (!forceRefresh || active.forceRefresh)) {
                    return sharedResult
                }
                continue
            }

            val mine = ownerSync!!
            var resolvedResult: ExternalAudioCatalogResult? = null
            try {
                resolvedResult = syncMutex.withLock {
                    withContext(Dispatchers.IO) {
                        executeSyncInternal(forceRefresh) { stage ->
                            mine.listeners.forEach { listener ->
                                runCatching { listener(stage) }
                            }
                        }
                    }
                }
                return resolvedResult
            } finally {
                mine.deferred.complete(resolvedResult)
                synchronized(inFlightLock) {
                    if (inFlightSync === mine) {
                        inFlightSync = null
                    }
                }
            }
        }
    }

    private suspend fun executeSyncInternal(
        forceRefresh: Boolean,
        emitStage: (ExternalAudioSyncStage) -> Unit,
    ): ExternalAudioCatalogResult {
        val cachedState = readCachedState()
        val cached = cachedState?.result
        val cachedEnvelope = cachedState?.envelope

        emitStage(ExternalAudioSyncStage.CONNECTING_PANEL)

        val panelResult = try {
            sonixControlRepository.fetchAudioSources()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            runCatching { Log.w(TAG, "Panel audio.sources failed (${e::class.java.simpleName})") }
            emitStage(ExternalAudioSyncStage.FAILED)
            val errMsg = e.message ?: "تعذّر جلب مصادر الصوت من الخادم"
            return fallbackOrError(
                cached = cached,
                errorMsg = errMsg,
                warningMsg = "فشلت المزامنة: $errMsg — تم الاحتفاظ بالنسخة المحلية",
            )
        }

        if (panelResult.isFailure) {
            emitStage(ExternalAudioSyncStage.FAILED)
            val panelErr = panelResult.exceptionOrNull()?.message ?: "تعذّر جلب مصادر الصوت من الخادم"
            return fallbackOrError(
                cached = cached,
                errorMsg = panelErr,
                warningMsg = "فشلت المزامنة: $panelErr — تم الاحتفاظ بالنسخة المحلية",
            )
        }

        val config = panelResult.getOrNull() ?: sonixControlRepository.state.value.audioSource
        val m3uUrl = config.m3uUrl?.trim().orEmpty()
        if (!config.configured || m3uUrl.isBlank()) {
            emitStage(ExternalAudioSyncStage.FAILED)
            val errMsg = "لم يتم ضبط قائمة الصوت الخارجي لهذا الجهاز بعد"
            return if (cached != null && cached.channels.isNotEmpty() && !forceRefresh) {
                cached.copy(
                    fromCache = true,
                    warning = errMsg,
                )
            } else if (cached != null && cached.channels.isNotEmpty()) {
                fallbackOrError(
                    cached = cached,
                    errorMsg = errMsg,
                    warningMsg = "فشلت المزامنة: $errMsg — تم الاحتفاظ بالنسخة المحلية",
                )
            } else {
                ExternalAudioCatalogResult(
                    channels = emptyList(),
                    error = errMsg,
                )
            }
        }

        val updatedAt = config.updatedAt?.trim().orEmpty()
        val sourceHash = sha256(m3uUrl)
        if (!forceRefresh &&
            cached != null &&
            cachedEnvelope != null &&
            cached.channels.isNotEmpty() &&
            cachedEnvelope.sourceHash == sourceHash &&
            (updatedAt.isEmpty() || cachedEnvelope.updatedAt == updatedAt)
        ) {
            emitStage(ExternalAudioSyncStage.SUCCEEDED)
            return cached.copy(fromCache = true)
        }

        return try {
            emitStage(ExternalAudioSyncStage.DOWNLOADING_PLAYLIST)
            val channels = downloadAndParseM3u(m3uUrl, emitStage)
            if (channels.isEmpty()) {
                throw AudioCatalogSyncException.ParseFailure(
                    "لم يتم العثور على قنوات beIN SPORTS أو ALWAN داخل قائمة M3U",
                )
            }

            emitStage(ExternalAudioSyncStage.SAVING_LOCAL)
            val syncedAtMs = clockMs()
            val envelope = CachedAudioCatalogEnvelope(
                version = 2,
                updatedAt = updatedAt,
                lastSyncedEpochMs = syncedAtMs,
                sourceHash = sourceHash,
                sourceLabel = safeHostLabel(m3uUrl),
                channels = channels,
            )
            try {
                persistEncrypted(envelope)
            } catch (storageError: Exception) {
                throw IllegalStateException("تعذّر حفظ بيانات الصوتيات محلياً على الجهاز", storageError)
            }

            val freshResult = ExternalAudioCatalogResult(
                channels = channels,
                updatedAt = updatedAt.ifBlank { null },
                lastSyncedAtMs = syncedAtMs,
                fromCache = false,
            )
            inMemoryCache.set(CachedAudioCatalogState(envelope, freshResult.copy(fromCache = true)))
            emitStage(ExternalAudioSyncStage.SUCCEEDED)
            freshResult
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: AudioCatalogSyncException) {
            // Never log M3U URL, query tokens, username, or password
            runCatching { Log.w(TAG, "External audio M3U sync failed (${e::class.java.simpleName})") }
            emitStage(ExternalAudioSyncStage.FAILED)
            fallbackOrError(
                cached = cached,
                errorMsg = e.userMessageAr,
                warningMsg = "فشلت المزامنة: ${e.userMessageAr} — تم الاحتفاظ بالنسخة المحلية",
            )
        } catch (e: Exception) {
            runCatching { Log.w(TAG, "External audio M3U sync failed (${e::class.java.simpleName})") }
            emitStage(ExternalAudioSyncStage.FAILED)
            val reason = e.message ?: "تعذّر تحميل قائمة الصوت الخارجي"
            fallbackOrError(
                cached = cached,
                errorMsg = reason,
                warningMsg = "فشلت المزامنة: $reason — تم الاحتفاظ بالنسخة المحلية",
            )
        }
    }

    private fun fallbackOrError(
        cached: ExternalAudioCatalogResult?,
        errorMsg: String,
        warningMsg: String,
    ): ExternalAudioCatalogResult {
        return if (cached != null && cached.channels.isNotEmpty()) {
            cached.copy(
                fromCache = true,
                warning = warningMsg,
                error = null,
            )
        } else {
            ExternalAudioCatalogResult(
                channels = emptyList(),
                error = errorMsg,
            )
        }
    }

    /**
     * Ported from SONIX AudioM3uCatalog: uses HTTP/1.1 HttpURLConnection with per-read
     * timeouts (no artificial total callTimeout cutting off large IPTV playlists),
     * explicit gzip handling, manual redirect checking, and isolated per-layer errors.
     */
    private fun downloadAndParseM3u(
        initialUrl: String,
        emitStage: (ExternalAudioSyncStage) -> Unit,
    ): List<ExternalAudioChannel> {
        val (connection, finalUrl) = openSafeM3uConnection(initialUrl)
        try {
            val contentType = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                .orEmpty()
            if (isRejectedContentType(contentType)) {
                throw AudioCatalogSyncException.ParseFailure(
                    "أعاد الخادم استجابة غير صالحة ($contentType) بدلاً من قائمة M3U",
                )
            }

            val declaredLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            if (declaredLength > AudioM3uParser.MAX_M3U_BYTES) {
                throw AudioCatalogSyncException.ParseFailure(
                    "حجم قائمة M3U تجاوز الحد الآمن (128 ميغابايت)",
                )
            }

            emitStage(ExternalAudioSyncStage.PARSING_CHANNELS)

            val rawStream = try {
                connection.inputStream
            } catch (e: SocketTimeoutException) {
                throw AudioCatalogSyncException.ReadTimeoutFailure(e)
            } catch (e: IOException) {
                throw AudioCatalogSyncException.ConnectFailure("تعذّر فتح تدفق قراءة قائمة الصوت", e)
            }

            val boundedRawStream = BoundedInputStream(rawStream, AudioM3uParser.MAX_M3U_BYTES)
            val stream = try {
                if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
                    GZIPInputStream(boundedRawStream, 64 * 1024)
                } else {
                    boundedRawStream
                }
            } catch (e: SocketTimeoutException) {
                throw AudioCatalogSyncException.ReadTimeoutFailure(e)
            } catch (e: IOException) {
                throw AudioCatalogSyncException.ParseFailure("تعذّر فك ضغط تدفق قائمة M3U", e)
            }

            return try {
                AudioM3uParser.parse(stream, finalUrl, dnsResolver)
            } catch (e: SocketTimeoutException) {
                throw AudioCatalogSyncException.ReadTimeoutFailure(e)
            } catch (e: EOFException) {
                throw AudioCatalogSyncException.ParseFailure("انقطع تدفق قائمة الصوتيات قبل اكتمالها", e)
            } catch (e: ZipException) {
                throw AudioCatalogSyncException.ParseFailure("ملف قائمة الصوتيات المضغوط غير مكتمل أو تالف", e)
            } catch (e: IOException) {
                throw AudioCatalogSyncException.ParseFailure("انقطعت قراءة قائمة الصوتيات من الخادم", e)
            } catch (e: IllegalStateException) {
                throw AudioCatalogSyncException.ParseFailure(e.message ?: "تنسيق قائمة الصوتيات غير صالح", e)
            }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun openSafeM3uConnection(rawUrl: String): Pair<HttpURLConnection, String> {
        var currentUrl = rawUrl
        var current = try {
            URL(currentUrl)
        } catch (e: MalformedURLException) {
            throw AudioCatalogSyncException.RedirectFailure("عنوان قائمة الصوتيات غير صالح", e)
        }

        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            validateDestinationSecurity(currentUrl, current)

            val connection = try {
                connectionFactory(current).apply {
                    requestMethod = "GET"
                    connectTimeout = m3uConnectTimeoutMs
                    readTimeout = m3uReadTimeoutMs
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", "SONIX-TV/1.0")
                    setRequestProperty("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
                    setRequestProperty("Accept-Encoding", "gzip")
                    setRequestProperty("Connection", "close")
                }
            } catch (e: Exception) {
                throw AudioCatalogSyncException.ConnectFailure("تعذّر إنشاء اتصال بخادم الصوت", e)
            }

            val status = try {
                connection.responseCode
            } catch (e: UnknownHostException) {
                runCatching { connection.disconnect() }
                throw AudioCatalogSyncException.DnsFailure(e)
            } catch (e: SocketTimeoutException) {
                runCatching { connection.disconnect() }
                if (e.message?.contains("connect", ignoreCase = true) == true) {
                    throw AudioCatalogSyncException.ConnectFailure("انتهت مهلة الاتصال بخادم قائمة الصوت", e)
                }
                throw AudioCatalogSyncException.ReadTimeoutFailure(e)
            } catch (e: ConnectException) {
                runCatching { connection.disconnect() }
                throw AudioCatalogSyncException.ConnectFailure("تعذّر الاتصال بخادم قائمة الصوت", e)
            } catch (e: NoRouteToHostException) {
                runCatching { connection.disconnect() }
                throw AudioCatalogSyncException.ConnectFailure("تعذّر الوصول إلى مسار خادم قائمة الصوت", e)
            } catch (e: SSLException) {
                runCatching { connection.disconnect() }
                throw AudioCatalogSyncException.ConnectFailure("فشل التحقق الأمني SSL للاتصال بخادم الصوت", e)
            } catch (e: IOException) {
                runCatching { connection.disconnect() }
                throw AudioCatalogSyncException.ConnectFailure("فشل الاتصال بخادم قائمة الصوت", e)
            }

            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                runCatching { connection.disconnect() }
                if (location.isNullOrBlank()) {
                    throw AudioCatalogSyncException.RedirectFailure("أعاد خادم الصوتيات تحويلاً دون وجهة")
                }
                if (redirectIndex >= MAX_REDIRECTS) {
                    throw AudioCatalogSyncException.RedirectFailure("تجاوزت قائمة الصوتيات عدد التحويلات المسموح")
                }
                current = try {
                    URL(current, location)
                } catch (e: MalformedURLException) {
                    throw AudioCatalogSyncException.RedirectFailure("رابط التحويل من خادم الصوتيات غير صالح", e)
                }
                currentUrl = current.toString()
            } else if (status !in 200..299) {
                runCatching { connection.disconnect() }
                throw AudioCatalogSyncException.HttpStatusFailure(status)
            } else {
                return connection to currentUrl
            }
        }
        throw AudioCatalogSyncException.RedirectFailure("تعذّر فتح قائمة الصوتيات بعد التحويلات")
    }

    private fun validateDestinationSecurity(urlString: String, parsed: URL) {
        val host = parsed.host?.removePrefix("[")?.removeSuffix("]")?.lowercase().orEmpty()
        try {
            AudioNetworkSecurityValidator.assertSafeUrl(urlString, dnsResolver)
        } catch (e: SecurityException) {
            throw AudioCatalogSyncException.RedirectFailure(e.message ?: "تم رفض عنوان غير آمن", e)
        } catch (e: IllegalArgumentException) {
            throw AudioCatalogSyncException.RedirectFailure(e.message ?: "عنوان الرابط غير صالح", e)
        }

        // Explicitly catch DNS failure separately for the M3U playlist host itself
        // unless host is an IP literal.
        if (host.isNotBlank() && !isIpLiteral(host)) {
            val addresses = try {
                dnsResolver(host)
            } catch (e: UnknownHostException) {
                throw AudioCatalogSyncException.DnsFailure(e)
            } catch (_: Exception) {
                null
            }
            if (addresses != null && addresses.any { AudioNetworkSecurityValidator.isBlockedAddress(it) }) {
                throw AudioCatalogSyncException.RedirectFailure("تم حظر الاتصال بعنوان محلي أو غير آمن")
            }
        }
    }

    private fun isIpLiteral(host: String): Boolean {
        return host.all { it.isDigit() || it == '.' } || host.contains(':')
    }

    private fun isRejectedContentType(mime: String): Boolean {
        if (mime.isEmpty()) return false
        return mime == "text/html" ||
            mime == "application/xhtml+xml" ||
            mime == "application/json" ||
            mime == "text/json" ||
            mime == "application/xml" ||
            mime == "text/xml" ||
            mime == "application/pdf" ||
            mime == "application/zip" ||
            mime.startsWith("image/") ||
            mime.startsWith("video/")
    }

    private fun readCachedState(): CachedAudioCatalogState? {
        inMemoryCache.get()?.let { return it }
        synchronized(cacheReadLock) {
            inMemoryCache.get()?.let { return it }
            val envelope = readCachedEnvelopeFromDisk() ?: return null
            if (envelope.channels.isEmpty()) return null
            val sorted = AudioSourceClassifier.sortChannels(envelope.channels)
            val syncedMs = envelope.lastSyncedEpochMs.takeIf { it > 0L }
            val result = ExternalAudioCatalogResult(
                channels = sorted,
                updatedAt = envelope.updatedAt.ifBlank { null },
                lastSyncedAtMs = syncedMs,
                fromCache = true,
            )
            val state = CachedAudioCatalogState(
                envelope = envelope.copy(channels = sorted),
                result = result,
            )
            inMemoryCache.set(state)
            return state
        }
    }

    private fun readCachedEnvelopeFromDisk(): CachedAudioCatalogEnvelope? {
        val file = cacheFile
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            val stored = file.readText(Charsets.UTF_8)
            val decrypted = credentialCrypto.decrypt(stored)
            json.decodeFromString<CachedAudioCatalogEnvelope>(decrypted)
        }.getOrNull()
    }

    private fun persistEncrypted(envelope: CachedAudioCatalogEnvelope) {
        val file = cacheFile
        val parent = file.parentFile ?: throw IllegalStateException("تعذر تحديد مجلد ذاكرة الصوت")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد ذاكرة الصوت")
        }
        val plaintext = json.encodeToString(envelope)
        val encrypted = credentialCrypto.encrypt(plaintext)
        val tempFile = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(encrypted.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val renamedByOs = runCatching {
                android.system.Os.rename(tempFile.absolutePath, file.absolutePath)
                file.isFile && !tempFile.exists()
            }.getOrDefault(false)
            if (!renamedByOs) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    throw IllegalStateException("تعذر حفظ ذاكرة الصوت بشكل ذري")
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun safeHostLabel(urlString: String): String {
        val uri = runCatching { URL(urlString) }.getOrNull() ?: return ""
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) return ""
        val port = uri.port
        return if (port > 0) "$host:$port" else host
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "ExternalAudioCatalog"
        private const val CACHE_FILE_NAME = "liquidtv_external_audio_catalog_v1.enc"
        private const val MAX_REDIRECTS = 5
        private const val DEFAULT_M3U_CONNECT_TIMEOUT_MS = 15_000
        private const val DEFAULT_M3U_READ_TIMEOUT_MS = 25_000
    }
}
