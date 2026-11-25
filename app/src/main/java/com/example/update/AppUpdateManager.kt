package com.example.update

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

object UpdateConfig {
    const val UPDATE_VERSION_URL =
        "https://drive.google.com/uc?export=download&id=1T013CXTJbJFbUkNc_euHiD3f147tzlTE"
    const val SHARE_APP_HEADER =
        "*صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا ﷺ*"
    const val SHARE_APP_IQBAL_QUOTE =
        "*✅️ڈاکٹر محمد اقبالؒ* سے منسوب ایک نقل : کہ مجھ پر یہ سب کرم نوازیاں گن کر ایک کروڑ مرتبہ درود پاک پڑھنے کے سبب ہے  تو اسی سے ترغیب لیتے ہوئے، آئیے 1 CRORE DUROOD TARGET مکمل کرنے کا عزم کریں۔"
    const val SHARE_APP_DATA_SAFETY_LINE_1 =
        "*✅️مزے کی بات😇* آپ کا سارا ڈیٹا Email+Password  پہ ہمیشہ کیلئےAuto  محفوظ ہوتا رہے گا کبھی ختم نہیں ہوگا.  "
    const val SHARE_APP_DATA_SAFETY_LINE_2 =
        "✅️بس آپ اپنا   Email+Password  یاد رکھیے گا 🫠"
    const val SHARE_APP_OFFLINE_ONLINE =
        "*Offline + Online*"
    const val SHARE_APP_SYNC_NOTE =
        "✅️بس ایک مرتبہ انٹرنیٹ کیساتھ دن میں یا ایک مہینے میں ایپ کو اوپن کرنا تا کہ درود کا سارا ڈیٹا  ہمیشہ کیلئے محفوظ ہو جائے۔"
    const val SHARE_APP_TASBIH_NOTE =
        "✅️اپنی counter Tasbih  پہ درود پاک پڑھ کر دن میں ایک مرتبہ آکر اپنا درود پاک  *1 Crore App*  میں input کر دیں🫰🏻 "
    const val SHARE_APP_INVITE_CTA =
        "✅️آج ہی اپنا سفر شروع کریں اور دوسروں کو بھی اس نیک عمل میں شریک کریں!"
    const val SHARE_APP_DOWNLOAD_CTA =
        "*📲 DOWNLOAD APK*"
    const val SHARE_APP_DOWNLOAD_POINTERS =
        "👇🏻👇🏻"
    const val SHARE_APP_FOOTER =
        "1 CRORE — ہر شمار، ایک قدم آگے!"
    const val SHARE_APP_TEXT_TITLE =
        "1 Crore — Durood Counter & Daily Target"
}

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val isForced: Boolean = false
)

sealed class ApkDownloadState {
    data class Downloading(
        val downloadId: Long,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : ApkDownloadState() {
        val progress: Float?
            get() = if (totalBytes > 0L) {
                (bytesDownloaded.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class ReadyToInstall(val apkFile: File) : ApkDownloadState()
    data class NeedsInstallPermission(val apkFile: File) : ApkDownloadState()
    data class Failed(val message: String) : ApkDownloadState()
}

/**
 * Abstraction for app update checks.
 * Currently backed by [DriveVersionJsonUpdateSource]; when publishing to Google Play,
 * swap to a Play In-App Updates implementation without changing UI or data-safety logic.
 */
interface UpdateSource {
    suspend fun fetchLatestRelease(): AppUpdateInfo?
}

class DriveVersionJsonUpdateSource(
    private val versionJsonUrl: String = UpdateConfig.UPDATE_VERSION_URL
) : UpdateSource {

    override suspend fun fetchLatestRelease(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        if (versionJsonUrl.isBlank() || versionJsonUrl.contains("REPLACE_WITH_")) {
            return@withContext null
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(versionJsonUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            if (code !in 200..299) return@withContext null

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.isBlank() || !body.trimStart().startsWith("{")) return@withContext null

            parseVersionJson(body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        fun parseVersionJson(jsonText: String): AppUpdateInfo? {
            return try {
                val obj = JSONObject(jsonText)
                val versionCode = obj.optInt("versionCode", -1)
                val versionName = obj.optString("versionName", "").trim()
                val downloadUrl = (
                    obj.optString("apkUrl", "").ifBlank {
                        obj.optString("downloadUrl", "").ifBlank {
                            obj.optString("apkDownloadUrl", "").ifBlank {
                                obj.optString("url", "")
                            }
                        }
                    }
                ).trim()
                val changelog = obj.optString("changelog", "").trim()
                val isForced = obj.optBoolean("isForced", false) ||
                    obj.optBoolean("forced", false) ||
                    obj.optBoolean("critical", false) ||
                    obj.optBoolean("isDataSafetyOrCritical", false)

                if (versionCode <= 0 || versionName.isEmpty() || !downloadUrl.startsWith("https://")) {
                    null
                } else {
                    AppUpdateInfo(
                        versionCode = versionCode,
                        versionName = versionName,
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                        isForced = isForced
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

        fun serialize(info: AppUpdateInfo): String {
            return JSONObject().apply {
                put("versionCode", info.versionCode)
                put("versionName", info.versionName)
                put("apkUrl", info.downloadUrl)
                put("changelog", info.changelog)
                put("isForced", info.isForced)
            }.toString()
        }
    }
}

private val Context.updatePrefs by preferencesDataStore(name = "update_prefs")

object AppUpdateManager {
    const val LATER_COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L
    private val KEY_DISMISSED_DATE = stringPreferencesKey("update_dismissed_date")
    private val KEY_DISMISSED_AT_MILLIS = longPreferencesKey("update_dismissed_at_millis")
    private val KEY_DISMISSED_VERSION = intPreferencesKey("update_dismissed_version")
    private val KEY_CACHED_UPDATE_JSON = stringPreferencesKey("cached_update_json")
    private val KEY_LATEST_APK_URL = stringPreferencesKey("latest_apk_url")
    private val KEY_LATEST_APK_VERSION_NAME = stringPreferencesKey("latest_apk_version_name")
    private val KEY_PENDING_DOWNLOAD_ID = longPreferencesKey("pending_download_id")

    @Volatile
    private var dismissedVersionInSession: Int = 0

    @Volatile
    private var latestApkUrlMemory: String? = null

    @Volatile
    private var latestApkVersionNameMemory: String? = null

    private val source: UpdateSource = DriveVersionJsonUpdateSource()

    data class ShareDownloadTarget(
        val downloadUrl: String?,
        val versionName: String?
    )

    data class ProgressShareCardSpec(
        val todayCount: Long,
        val totalCount: Long,
        val targetCount: Long = 10_000_000L,
        val todayFormatted: String,
        val totalFormatted: String,
        val progressFraction: Float,
        val percentFormatted: String,
        val downloadUrl: String,
        val shareCaptionText: String,
        val renderedAtMillis: Long
    )

    @Volatile
    private var lastProgressCardSpecForTest: ProgressShareCardSpec? = null

    @Volatile
    private var lastProgressCardBitmapForTest: Bitmap? = null

    @Volatile
    private var lastShareModeForTest: String? = null

    @Volatile
    private var shareTargetOverrideForTest: ShareDownloadTarget? = null

    private val progressCardRenderCountForTest = java.util.concurrent.atomic.AtomicInteger(0)
    private val simpleShareCountForTest = java.util.concurrent.atomic.AtomicInteger(0)

    fun getLastProgressCardSpecForTest(): ProgressShareCardSpec? = lastProgressCardSpecForTest
    fun getLastProgressCardBitmapForTest(): Bitmap? = lastProgressCardBitmapForTest
    fun getLastShareModeForTest(): String? = lastShareModeForTest
    fun getProgressCardRenderCountForTest(): Int = progressCardRenderCountForTest.get()
    fun getSimpleShareCountForTest(): Int = simpleShareCountForTest.get()

    fun setShareTargetOverrideForTest(target: ShareDownloadTarget?) {
        shareTargetOverrideForTest = target
    }

    fun resetShareStateForTest(context: Context? = null) {
        lastProgressCardSpecForTest = null
        lastProgressCardBitmapForTest = null
        lastShareModeForTest = null
        shareTargetOverrideForTest = null
        progressCardRenderCountForTest.set(0)
        simpleShareCountForTest.set(0)
        if (context != null) {
            runCatching {
                File(File(context.applicationContext.cacheDir, "share"), PROGRESS_CARD_FILE_NAME).delete()
            }
        }
    }

    fun resolveEffectiveShareDownloadUrl(rawUrl: String? = null): String {
        val candidate = rawUrl?.trim()?.takeIf { it.startsWith("https://") }
            ?: shareTargetOverrideForTest?.downloadUrl?.trim()?.takeIf { it.startsWith("https://") }
            ?: latestApkUrlMemory?.trim()?.takeIf { it.startsWith("https://") }
        return candidate ?: ""
    }

    private const val PROGRESS_CARD_FILE_NAME = "progress_share_card.png"
    private const val ONE_CRORE_TARGET = 10_000_000L

    suspend fun checkForUpdate(
        context: Context,
        installedVersionCode: Int = BuildConfig.VERSION_CODE
    ): AppUpdateInfo? {
        val remote = source.fetchLatestRelease()
        if (remote == null) {
            return getCachedAvailableUpdate(context, installedVersionCode)
        }
        if (remote.downloadUrl.startsWith("https://")) {
            latestApkUrlMemory = remote.downloadUrl
            latestApkVersionNameMemory = remote.versionName.takeIf { it.isNotBlank() }
            saveLatestApkInfo(context, remote.downloadUrl, remote.versionName)
        }
        return if (remote.versionCode > installedVersionCode) {
            saveCachedUpdate(context, remote)
            remote
        } else {
            clearCachedUpdate(context)
            null
        }
    }

    suspend fun resolveShareDownloadUrl(context: Context): String? =
        resolveShareDownloadTarget(context).downloadUrl

    suspend fun resolveShareDownloadTarget(context: Context): ShareDownloadTarget = withContext(Dispatchers.IO) {
        shareTargetOverrideForTest?.let { return@withContext it }
        val remote = withTimeoutOrNull(2_500L) { source.fetchLatestRelease() }
        if (remote != null && remote.downloadUrl.startsWith("https://")) {
            val remoteVersion = remote.versionName.trim().takeIf { it.isNotEmpty() }
            latestApkUrlMemory = remote.downloadUrl
            latestApkVersionNameMemory = remoteVersion
            saveLatestApkInfo(context, remote.downloadUrl, remoteVersion)
            return@withContext ShareDownloadTarget(
                downloadUrl = remote.downloadUrl,
                versionName = remoteVersion ?: BuildConfig.VERSION_NAME
            )
        }

        val memUrl = latestApkUrlMemory?.trim().orEmpty()
        val memVersion = latestApkVersionNameMemory?.trim()?.takeIf { it.isNotEmpty() }
        if (memUrl.startsWith("https://")) {
            return@withContext ShareDownloadTarget(
                downloadUrl = memUrl,
                versionName = memVersion ?: BuildConfig.VERSION_NAME
            )
        }

        try {
            val prefs = context.updatePrefs.data.first()
            val savedUrl = prefs[KEY_LATEST_APK_URL]?.trim().orEmpty()
            val savedVersion = prefs[KEY_LATEST_APK_VERSION_NAME]?.trim()?.takeIf { it.isNotEmpty() }
            if (savedUrl.startsWith("https://")) {
                latestApkUrlMemory = savedUrl
                latestApkVersionNameMemory = savedVersion
                return@withContext ShareDownloadTarget(
                    downloadUrl = savedUrl,
                    versionName = savedVersion ?: BuildConfig.VERSION_NAME
                )
            }
            val cachedRaw = prefs[KEY_CACHED_UPDATE_JSON]
            val parsed = cachedRaw?.let { DriveVersionJsonUpdateSource.parseVersionJson(it) }
            if (parsed != null && parsed.downloadUrl.startsWith("https://")) {
                val parsedVersion = parsed.versionName.trim().takeIf { it.isNotEmpty() }
                latestApkUrlMemory = parsed.downloadUrl
                latestApkVersionNameMemory = parsedVersion
                return@withContext ShareDownloadTarget(
                    downloadUrl = parsed.downloadUrl,
                    versionName = parsedVersion ?: BuildConfig.VERSION_NAME
                )
            }
        } catch (_: Exception) {
        }

        ShareDownloadTarget(
            downloadUrl = null,
            versionName = BuildConfig.VERSION_NAME
        )
    }

    fun buildShareText(
        downloadUrl: String?,
        versionName: String? = BuildConfig.VERSION_NAME
    ): String = buildString {
        val resolvedVersionName = versionName?.trim().takeIf { !it.isNullOrEmpty() }
            ?: BuildConfig.VERSION_NAME
        appendLine(UpdateConfig.SHARE_APP_HEADER)
        appendLine(UpdateConfig.SHARE_APP_IQBAL_QUOTE)
        appendLine()
        appendLine(UpdateConfig.SHARE_APP_DATA_SAFETY_LINE_1)
        appendLine(UpdateConfig.SHARE_APP_DATA_SAFETY_LINE_2)
        appendLine(UpdateConfig.SHARE_APP_OFFLINE_ONLINE)
        appendLine(UpdateConfig.SHARE_APP_SYNC_NOTE)
        appendLine(UpdateConfig.SHARE_APP_TASBIH_NOTE)
        appendLine(UpdateConfig.SHARE_APP_INVITE_CTA)
        appendLine()
        if (!downloadUrl.isNullOrBlank()) {
            appendLine(UpdateConfig.SHARE_APP_DOWNLOAD_CTA)
        appendLine()
            appendLine(UpdateConfig.SHARE_APP_DOWNLOAD_POINTERS)
        }
        if (!downloadUrl.isNullOrBlank()) {
            appendLine(downloadUrl)
        }
        appendLine()
        appendLine("APP VERSION: *$resolvedVersionName*")
        appendLine()
        append(UpdateConfig.SHARE_APP_FOOTER)
    }

    fun buildProgressShareCardSpec(
        todayCount: Long,
        totalCount: Long,
        downloadUrl: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): ProgressShareCardSpec {
        val safeToday = todayCount.coerceAtLeast(0L)
        val safeTotal = totalCount.coerceAtLeast(0L)
        val numberFormat = java.text.NumberFormat.getNumberInstance(Locale.US)
        val todayFormatted = numberFormat.format(safeToday)
        val totalFormatted = numberFormat.format(safeTotal)
        val rawPercent = (safeTotal.toDouble() / ONE_CRORE_TARGET.toDouble()) * 100.0
        val clampedPercent = rawPercent.coerceIn(0.0, 100.0)
        val percentFormatted = String.format(Locale.US, "%.2f%%", clampedPercent)
        val progressFraction = (safeTotal.toDouble() / ONE_CRORE_TARGET.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
        val resolvedDownloadUrl = downloadUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
        val caption = buildString {
            appendLine(UpdateConfig.SHARE_APP_HEADER)
            appendLine("Today: $todayFormatted · Total: $totalFormatted / 10,000,000 ($percentFormatted)")
            appendLine()
            appendLine(UpdateConfig.SHARE_APP_DOWNLOAD_CTA)
            if (resolvedDownloadUrl.isNotBlank()) {
                appendLine(resolvedDownloadUrl)
            }
        }.trim()
        return ProgressShareCardSpec(
            todayCount = safeToday,
            totalCount = safeTotal,
            targetCount = ONE_CRORE_TARGET,
            todayFormatted = todayFormatted,
            totalFormatted = totalFormatted,
            progressFraction = progressFraction,
            percentFormatted = percentFormatted,
            downloadUrl = resolvedDownloadUrl,
            shareCaptionText = caption,
            renderedAtMillis = nowMillis
        )
    }

    fun renderProgressShareCardBitmap(spec: ProgressShareCardSpec): Bitmap {
        val width = 1080
        val height = 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Strictly black & white / monochrome palette (R == G == B for every color)
        val pureWhite = Color.WHITE
        val pureBlack = Color.BLACK
        val subtextGrey = Color.rgb(85, 85, 85)
        val hairlineGrey = Color.rgb(224, 224, 224)
        val offWhite = Color.rgb(247, 247, 247)

        canvas.drawColor(pureWhite)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = pureBlack
        }
        val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = hairlineGrey
        }
        val fillBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = pureBlack
        }
        val fillOffWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = offWhite
        }
        val fillTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = hairlineGrey
        }

        // Outer monochrome frame
        canvas.drawRoundRect(36f, 36f, width - 36f, height - 36f, 36f, 36f, borderPaint)

        // Header label & Arabic blessing
        val headerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pureBlack
            textSize = 30f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("1 CRORE DUROOD TARGET", 96f, 126f, headerLabelPaint)

        val arabicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subtextGrey
            textSize = 32f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        canvas.drawText("صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا ﷺ", 96f, 178f, arabicPaint)

        canvas.drawLine(96f, 214f, width - 96f, 214f, hairlinePaint)

        // Hero Section: TOTAL COUNT + % Badge
        val sectionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subtextGrey
            textSize = 26f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            letterSpacing = 0.06f
        }
        canvas.drawText("TOTAL COUNT", 96f, 286f, sectionLabelPaint)

        // Black pill badge for % progress on top-right of Hero section
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pureWhite
            textSize = 30f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val badgeWidth = 220f
        val badgeLeft = width - 96f - badgeWidth
        val badgeTop = 244f
        val badgeBottom = 304f
        canvas.drawRoundRect(badgeLeft, badgeTop, width - 96f, badgeBottom, 30f, 30f, fillBlackPaint)
        canvas.drawText(spec.percentFormatted, badgeLeft + badgeWidth / 2f, 285f, badgeTextPaint)

        // Giant Total number
        val totalNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pureBlack
            textSize = if (spec.totalFormatted.length > 9) 88f else 104f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(spec.totalFormatted, 96f, 412f, totalNumberPaint)

        val targetSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subtextGrey
            textSize = 30f
        }
        canvas.drawText("out of 10,000,000 (1 Crore)", 96f, 466f, targetSubPaint)

        // Progress Bar toward 1 Crore
        val barLeft = 96f
        val barRight = width - 96f
        val barTop = 508f
        val barBottom = 532f
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, 12f, 12f, fillTrackPaint)
        val visualFraction = if (spec.totalCount > 0L) {
            spec.progressFraction.coerceAtLeast(0.02f)
        } else {
            0f
        }
        if (visualFraction > 0f) {
            val fillRight = barLeft + (barRight - barLeft) * visualFraction
            canvas.drawRoundRect(barLeft, barTop, fillRight, barBottom, 12f, 12f, fillBlackPaint)
        }

        // Two side-by-side stat boxes: TODAY COUNT & % TOWARD 1 CRORE
        val boxTop = 584f
        val boxBottom = 796f
        val gap = 28f
        val boxWidth = (width - 192f - gap) / 2f
        val leftBoxRight = 96f + boxWidth
        val rightBoxLeft = leftBoxRight + gap
        val rightBoxRight = width - 96f

        canvas.drawRoundRect(96f, boxTop, leftBoxRight, boxBottom, 24f, 24f, fillOffWhitePaint)
        canvas.drawRoundRect(96f, boxTop, leftBoxRight, boxBottom, 24f, 24f, hairlinePaint)

        canvas.drawRoundRect(rightBoxLeft, boxTop, rightBoxRight, boxBottom, 24f, 24f, fillOffWhitePaint)
        canvas.drawRoundRect(rightBoxLeft, boxTop, rightBoxRight, boxBottom, 24f, 24f, hairlinePaint)

        canvas.drawText("TODAY COUNT", 132f, boxTop + 68f, sectionLabelPaint)
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pureBlack
            textSize = 56f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(spec.todayFormatted, 132f, boxTop + 154f, statValuePaint)

        canvas.drawText("% OF 1 CRORE", rightBoxLeft + 36f, boxTop + 68f, sectionLabelPaint)
        canvas.drawText(spec.percentFormatted, rightBoxLeft + 36f, boxTop + 154f, statValuePaint)

        // Footer with App Download Link
        canvas.drawLine(96f, 846f, width - 96f, 846f, hairlinePaint)
        canvas.drawText("DOWNLOAD 1 CRORE APP", 96f, 902f, sectionLabelPaint)

        val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pureBlack
            textSize = 24f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val linkDisplay = if (spec.downloadUrl.length > 58) {
            spec.downloadUrl.take(55) + "..."
        } else {
            spec.downloadUrl
        }
        canvas.drawText(linkDisplay, 96f, 954f, linkPaint)

        lastProgressCardSpecForTest = spec
        lastProgressCardBitmapForTest = bitmap
        return bitmap
    }

    suspend fun shareProgressCard(
        context: Context,
        todayCount: Long,
        totalCount: Long,
        downloadUrlOverride: String? = null
    ) {
        val appContext = context.applicationContext
        val shareTarget = resolveShareDownloadTarget(appContext)
        val resolvedUrl = resolveEffectiveShareDownloadUrl(downloadUrlOverride ?: shareTarget.downloadUrl)
        val spec = buildProgressShareCardSpec(
            todayCount = todayCount,
            totalCount = totalCount,
            downloadUrl = resolvedUrl
        )
        val cardUri = withContext(Dispatchers.IO) {
            try {
                val shareDir = File(appContext.cacheDir, "share")
                if (!shareDir.exists()) {
                    shareDir.mkdirs()
                }
                // Always overwrite with a freshly rendered bitmap so numbers are NEVER cached/stale
                val cardFile = File(shareDir, PROGRESS_CARD_FILE_NAME)
                val bitmap = renderProgressShareCardBitmap(spec)
                FileOutputStream(cardFile, false).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                lastProgressCardBitmapForTest = bitmap
                FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    cardFile
                )
            } catch (_: Exception) {
                null
            }
        }
        lastProgressCardSpecForTest = spec
        lastShareModeForTest = "progress_card"
        progressCardRenderCountForTest.incrementAndGet()

        withContext(Dispatchers.Main) {
            if (cardUri != null) {
                try {
                    val clip = ClipData(
                        ClipDescription(
                            UpdateConfig.SHARE_APP_TEXT_TITLE,
                            arrayOf("image/png", ClipDescription.MIMETYPE_TEXT_PLAIN)
                        ),
                        ClipData.Item(spec.shareCaptionText, null, null, cardUri)
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, cardUri)
                        putExtra(Intent.EXTRA_TEXT, spec.shareCaptionText)
                        putExtra(Intent.EXTRA_TITLE, UpdateConfig.SHARE_APP_TEXT_TITLE)
                        putExtra(Intent.EXTRA_SUBJECT, UpdateConfig.SHARE_APP_TEXT_TITLE)
                        clipData = clip
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(sendIntent, UpdateConfig.SHARE_APP_TEXT_TITLE).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (context !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(chooser)
                    return@withContext
                } catch (_: Exception) {
                }
            }
            try {
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, spec.shareCaptionText)
                    putExtra(Intent.EXTRA_TITLE, UpdateConfig.SHARE_APP_TEXT_TITLE)
                    putExtra(Intent.EXTRA_SUBJECT, UpdateConfig.SHARE_APP_TEXT_TITLE)
                }
                val chooser = Intent.createChooser(textIntent, UpdateConfig.SHARE_APP_TEXT_TITLE).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun shareApp(context: Context) {
        lastShareModeForTest = "simple_progress"
        simpleShareCountForTest.incrementAndGet()
        val appContext = context.applicationContext
        val shareTarget = resolveShareDownloadTarget(appContext)
        val shareText = buildShareText(shareTarget.downloadUrl, shareTarget.versionName)
        val logoUri = withContext(Dispatchers.IO) {
            prepareShareLogoUri(appContext)
        }

        withContext(Dispatchers.Main) {
            if (logoUri != null) {
                try {
                    val clip = ClipData(
                        ClipDescription(
                            UpdateConfig.SHARE_APP_TEXT_TITLE,
                            arrayOf("image/png", ClipDescription.MIMETYPE_TEXT_PLAIN)
                        ),
                        ClipData.Item(shareText, null, null, logoUri)
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, logoUri)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_TITLE, UpdateConfig.SHARE_APP_TEXT_TITLE)
                        putExtra(Intent.EXTRA_SUBJECT, UpdateConfig.SHARE_APP_TEXT_TITLE)
                        clipData = clip
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(sendIntent, UpdateConfig.SHARE_APP_TEXT_TITLE).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (context !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(chooser)
                    return@withContext
                } catch (_: Exception) {
                    // Fall back to text-only share sheet if image sharing fails
                }
            }

            try {
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_TITLE, UpdateConfig.SHARE_APP_TEXT_TITLE)
                    putExtra(Intent.EXTRA_SUBJECT, UpdateConfig.SHARE_APP_TEXT_TITLE)
                }
                val chooser = Intent.createChooser(textIntent, UpdateConfig.SHARE_APP_TEXT_TITLE).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
            }
        }
    }

    internal const val SHARE_LOGO_ASSET_VERSION = "v11_small_dot"

    internal fun resolveShareLogoDrawableResId(): Int = R.drawable.ic_one_crore_foreground

    internal fun prepareShareLogoUri(context: Context): Uri? {
        return try {
            val shareDir = File(context.cacheDir, "share")
            if (!shareDir.exists()) {
                shareDir.mkdirs()
            }
            val logoFile = File(shareDir, "1_crore_logo.png")
            val markerFile = File(shareDir, "1_crore_logo.version")
            val currentVersion = runCatching {
                if (markerFile.exists()) markerFile.readText().trim() else null
            }.getOrNull()
            if (!logoFile.exists() || logoFile.length() == 0L || currentVersion != SHARE_LOGO_ASSET_VERSION) {
                val fgBitmap = BitmapFactory.decodeResource(
                    context.resources,
                    resolveShareLogoDrawableResId()
                ) ?: return null
                val size = maxOf(fgBitmap.width, fgBitmap.height, 512)
                val combined = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(combined)
                canvas.drawColor(Color.BLACK)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(fgBitmap, null, Rect(0, 0, size, size), paint)
                FileOutputStream(logoFile, false).use { out ->
                    combined.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                markerFile.writeText(SHARE_LOGO_ASSET_VERSION)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logoFile
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getCachedAvailableUpdate(
        context: Context,
        installedVersionCode: Int = BuildConfig.VERSION_CODE
    ): AppUpdateInfo? {
        val prefs = context.updatePrefs.data.first()
        val raw = prefs[KEY_CACHED_UPDATE_JSON] ?: return null
        val parsed = DriveVersionJsonUpdateSource.parseVersionJson(raw)
        if (parsed != null && parsed.downloadUrl.startsWith("https://")) {
            latestApkUrlMemory = parsed.downloadUrl
            latestApkVersionNameMemory = parsed.versionName.takeIf { it.isNotBlank() }
        }
        if (parsed == null || parsed.versionCode <= installedVersionCode) {
            clearCachedUpdate(context)
            return null
        }
        return parsed
    }

    suspend fun shouldAutoShowDialog(
        context: Context,
        update: AppUpdateInfo,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (update.isForced) return true
        if (dismissedVersionInSession >= update.versionCode) return false
        val prefs = context.updatePrefs.data.first()
        val dismissedVersion = prefs[KEY_DISMISSED_VERSION] ?: 0
        if (dismissedVersion < update.versionCode) return true

        val dismissedAtMillis = prefs[KEY_DISMISSED_AT_MILLIS] ?: 0L
        if (dismissedAtMillis > 0L) {
            val elapsed = nowMillis - dismissedAtMillis
            return elapsed >= LATER_COOLDOWN_MILLIS || elapsed < 0L
        }
        val dismissedDate = prefs[KEY_DISMISSED_DATE] ?: ""
        val today = LocalDate.now().toString()
        return dismissedDate != today
    }

    suspend fun markDismissedForToday(
        context: Context,
        versionCode: Int,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        dismissedVersionInSession = versionCode
        context.updatePrefs.edit { mutable ->
            mutable[KEY_DISMISSED_DATE] = LocalDate.now().toString()
            mutable[KEY_DISMISSED_AT_MILLIS] = nowMillis
            mutable[KEY_DISMISSED_VERSION] = versionCode
        }
    }

    fun clearSessionDismissalForTest() {
        dismissedVersionInSession = 0
    }

    suspend fun resetDismissedStateForTest(context: Context) {
        dismissedVersionInSession = 0
        context.updatePrefs.edit { mutable ->
            mutable.remove(KEY_DISMISSED_DATE)
            mutable.remove(KEY_DISMISSED_AT_MILLIS)
            mutable.remove(KEY_DISMISSED_VERSION)
        }
    }

    private suspend fun saveLatestApkInfo(context: Context, apkUrl: String, versionName: String?) {
        try {
            context.updatePrefs.edit { mutable ->
                mutable[KEY_LATEST_APK_URL] = apkUrl
                val trimmedVersion = versionName?.trim().orEmpty()
                if (trimmedVersion.isNotEmpty()) {
                    mutable[KEY_LATEST_APK_VERSION_NAME] = trimmedVersion
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun saveCachedUpdate(context: Context, update: AppUpdateInfo) {
        context.updatePrefs.edit { mutable ->
            mutable[KEY_CACHED_UPDATE_JSON] = DriveVersionJsonUpdateSource.serialize(update)
            if (update.downloadUrl.startsWith("https://")) {
                mutable[KEY_LATEST_APK_URL] = update.downloadUrl
                if (update.versionName.isNotBlank()) {
                    mutable[KEY_LATEST_APK_VERSION_NAME] = update.versionName.trim()
                }
            }
        }
    }

    private suspend fun clearCachedUpdate(context: Context) {
        context.updatePrefs.edit { mutable ->
            mutable.remove(KEY_CACHED_UPDATE_JSON)
        }
    }

    private suspend fun findResumableDownloadId(context: Context): Long {
        val dm = context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return -1L
        val persistedId = context.applicationContext.updatePrefs.data.first()[KEY_PENDING_DOWNLOAD_ID] ?: return -1L
        if (persistedId <= 0L) return -1L
        var status = -1
        dm.query(DownloadManager.Query().setFilterById(persistedId))?.use { cursor ->
            if (cursor.moveToFirst()) {
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
        }
        return if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING) {
            persistedId
        } else {
            context.applicationContext.updatePrefs.edit { it.remove(KEY_PENDING_DOWNLOAD_ID) }
            -1L
        }
    }

    fun downloadApk(context: Context, update: AppUpdateInfo): Flow<ApkDownloadState> = flow {
        if (!update.downloadUrl.startsWith("https://")) {
            emit(ApkDownloadState.Failed("Invalid APK download link."))
            return@flow
        }

        val appContext = context.applicationContext
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) {
            emit(ApkDownloadState.Failed("Storage is unavailable for downloading updates."))
            return@flow
        }
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val fileName = "1crore-update-${update.versionCode}.apk"
        val destFile = File(downloadsDir, fileName)
        val resumedId = findResumableDownloadId(appContext)
        if (resumedId < 0L && destFile.exists()) {
            destFile.delete()
        }

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            emit(ApkDownloadState.Failed("DownloadManager is unavailable on this device."))
            return@flow
        }

        val downloadId = if (resumedId > 0L) resumedId else try {
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl)).apply {
                setTitle("1 Crore ${update.versionName}")
                setDescription("Downloading app update")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            dm.enqueue(request)
        } catch (e: Exception) {
            emit(ApkDownloadState.Failed(e.localizedMessage ?: "Could not start APK download."))
            return@flow
        }

        if (resumedId < 0L) {
            appContext.updatePrefs.edit { it[KEY_PENDING_DOWNLOAD_ID] = downloadId }
        }
emit(ApkDownloadState.Downloading(downloadId, bytesDownloaded = 0L, totalBytes = -1L))

        val query = DownloadManager.Query().setFilterById(downloadId)
        var finishedNormally = false
        var lastProgressBytes = 0L
        var lastProgressAtMillis = System.currentTimeMillis()
        try {
            while (true) {
                var status = -1
                var bytesDownloaded = 0L
                var totalBytes = -1L
                var reason = 0
                var hasRow = false

                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        hasRow = true
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        bytesDownloaded = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        totalBytes = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }
                }

                if (!hasRow) {
                    runCatching { if (destFile.exists()) destFile.delete() }
                    emit(ApkDownloadState.Failed("Download was cancelled."))
                    finishedNormally = true
                    break
                }

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        finishedNormally = true
                        if (!destFile.exists() || destFile.length() == 0L || !isValidApkFile(destFile)) {
                            runCatching { destFile.delete() }
                            runCatching { dm.remove(downloadId) }
                            emit(
                                ApkDownloadState.Failed(
                                    "Downloaded file is not a valid APK package. Check the release APK URL."
                                )
                            )
                        } else if (canInstallPackages(appContext)) {
                            emit(ApkDownloadState.ReadyToInstall(destFile))
                        } else {
                            emit(ApkDownloadState.NeedsInstallPermission(destFile))
                        }
                        break
                    }

                    DownloadManager.STATUS_FAILED -> {
                        finishedNormally = true
                        runCatching { dm.remove(downloadId) }
                        runCatching { if (destFile.exists()) destFile.delete() }
                        emit(ApkDownloadState.Failed(mapFailureReason(reason)))
                        break
                    }

                    else -> {
                        if (bytesDownloaded > lastProgressBytes) {
                        lastProgressBytes = bytesDownloaded
                        lastProgressAtMillis = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - lastProgressAtMillis > 60_000L) {
                        runCatching { dm.remove(downloadId) }
                        runCatching { if (destFile.exists()) destFile.delete() }
                        appContext.updatePrefs.edit { it.remove(KEY_PENDING_DOWNLOAD_ID) }
                        emit(ApkDownloadState.Failed("Download stalled. Please check your connection and try again."))
                        finishedNormally = true
                        break
                    }
emit(ApkDownloadState.Downloading(downloadId, bytesDownloaded, totalBytes))
                    }
                }

                delay(350L)
            }
        } catch (e: CancellationException) {
            if (!finishedNormally) {
                runCatching { dm.remove(downloadId) }
                runCatching { if (destFile.exists()) destFile.delete() }
            }
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun cancelDownload(context: Context, downloadId: Long?, versionCode: Int? = null) {
        if (downloadId != null) {
            try {
                val dm = context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(downloadId)
            } catch (_: Exception) {
            }
        }
        cleanupPartialApkFiles(context, versionCode)
    }

    fun cleanupPartialApkFiles(context: Context, versionCode: Int? = null) {
        try {
            val downloadsDir = context.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            if (!downloadsDir.exists()) return
            if (versionCode != null) {
                val target = File(downloadsDir, "1crore-update-$versionCode.apk")
                if (target.exists()) target.delete()
            } else {
                downloadsDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("1crore-update-") && file.name.endsWith(".apk")) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false
        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatDownloadProgressText(bytesDownloaded: Long, totalBytes: Long): String {
        val downloadedMb = String.format(Locale.US, "%.1f MB", bytesDownloaded / (1024.0 * 1024.0))
        return if (totalBytes > 0L) {
            val totalMb = String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0))
            val pct = ((bytesDownloaded.toDouble() / totalBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
            "Downloading $downloadedMb / $totalMb ($pct%)"
        } else {
            "Downloading update ($downloadedMb)..."
        }
    }

    internal fun isValidApkFile(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 4L) return false
            FileInputStream(file).use { input ->
                val header = ByteArray(2)
                input.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    internal fun mapFailureReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space to download update."
            DownloadManager.ERROR_CANNOT_RESUME -> "Download could not be resumed. Please try again."
            DownloadManager.ERROR_HTTP_DATA_ERROR,
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Network error while downloading APK. Please try again."
            else -> "Could not download the update APK. Please check your connection and try again."
        }
    }
}
