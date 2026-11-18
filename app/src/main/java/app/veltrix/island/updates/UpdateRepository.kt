package app.veltrix.island.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSize: Long?
)

data class UpdateCheck(
    val status: String,
    val message: String,
    val release: ReleaseInfo? = null,
    val checkedAt: String = ""
)

object UpdateRepository {
    suspend fun check(repository: String, currentVersion: String): UpdateCheck = withContext(Dispatchers.IO) {
        val repo = repository.trim().trim('/')
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Enter a GitHub repository as owner/repository." }
        val checked = formatNowWib()
        val release = runCatching { getLatestRelease(repo) }.getOrNull()
        if (release != null) {
            val hasNewer = compareVersions(release.tag, currentVersion) > 0
            if (hasNewer) return@withContext UpdateCheck("available", "A newer release is available.", release, checked)
            val updateState = fetchUpdateState(repo)
            if (updateState?.optString("status") == "development") {
                return@withContext UpdateCheck("development", updateState.optString("message", "The next update is being developed."), release, checked)
            }
            return@withContext UpdateCheck("none", updateState?.optString("message")?.takeIf { it.isNotBlank() } ?: "You are using the latest published version.", release, checked)
        }
        val updateState = fetchUpdateState(repo)
        if (updateState?.optString("status") == "development") {
            UpdateCheck("development", updateState.optString("message", "The next update is being developed."), null, checked)
        } else if (updateState?.optString("status") == "none") {
            UpdateCheck("none", updateState.optString("message", "There is no update available."), null, checked)
        } else {
            UpdateCheck("error", "No public release was found. Check repository visibility and release setup.", null, checked)
        }
    }

    private fun getLatestRelease(repo: String): ReleaseInfo {
        val json = requestJson("https://api.github.com/repos/$repo/releases/latest")
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize: Long? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkName = name
                    apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    apkSize = asset.optLong("size")
                    break
                }
            }
        }
        return ReleaseInfo(
            tag = json.optString("tag_name", ""),
            name = json.optString("name", json.optString("tag_name", "Release")),
            body = json.optString("body", "No release notes were added."),
            publishedAt = json.optString("published_at", ""),
            htmlUrl = json.optString("html_url", ""),
            apkUrl = apkUrl,
            apkName = apkName,
            apkSize = apkSize
        )
    }

    private fun fetchUpdateState(repo: String): JSONObject? = runCatching {
        requestJson("https://raw.githubusercontent.com/$repo/main/update/update.json")
    }.getOrNull()

    private fun requestJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VELTRIX-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("GitHub request failed: HTTP $code")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        fun parts(value: String) = value.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val aa = parts(a)
        val bb = parts(b)
        for (i in 0 until maxOf(aa.size, bb.size)) {
            val left = aa.getOrElse(i) { 0 }
            val right = bb.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun formatNowWib(): String = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }.format(Date())

    fun formatPublishedAt(iso: String): String {
        if (iso.isBlank()) return "Unknown release date"
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = parser.parse(iso) ?: return "Unknown release date"
            SimpleDateFormat("dd MMM yyyy · HH:mm 'WIB'", Locale("id", "ID")).apply {
                timeZone = TimeZone.getTimeZone("Asia/Jakarta")
            }.format(date)
        }.getOrDefault("Unknown release date")
    }

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "Unknown size"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
