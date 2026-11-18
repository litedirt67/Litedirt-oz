package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private data class BrowserBookmark(val title: String, val subtitle: String, val url: String)

private data class ExtractedWebLink(
    val label: String,
    val url: String,
    val isDownloadable: Boolean
)

private data class WebPageResult(
    val url: String,
    val title: String,
    val textSnippet: String,
    val links: List<ExtractedWebLink>,
    val error: String? = null
)

private val QUICK_BOOKMARKS = listOf(
    BrowserBookmark("F-Droid", "f-droid.org", "https://f-droid.org/en/packages/"),
    BrowserBookmark("GitHub", "github.com", "https://github.com/explore"),
    BrowserBookmark("DuckDuckGo", "lite.duckduckgo.com", "https://lite.duckduckgo.com/lite/"),
    BrowserBookmark("Wikipedia", "en.wikipedia.org", "https://en.m.wikipedia.org")
)

@Composable
fun VmBrowserPane(
    initialUrl: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onDownloadFile: (url: String, contentDisposition: String?, mimeType: String?) -> Unit,
    onCloseBrowser: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var addressText by remember { mutableStateOf(initialUrl.ifBlank { "litedirt://home" }) }
    var currentUrl by remember { mutableStateOf(initialUrl.ifBlank { "litedirt://home" }) }
    var isLoading by remember { mutableStateOf(false) }
    var pageResult by remember { mutableStateOf<WebPageResult?>(null) }

    val backStack = remember { mutableStateListOf<String>() }
    val forwardStack = remember { mutableStateListOf<String>() }

    fun loadTarget(raw: String, pushHistory: Boolean = true) {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "litedirt://home") {
            if (pushHistory && currentUrl != "litedirt://home") {
                backStack.add(currentUrl)
                forwardStack.clear()
            }
            currentUrl = "litedirt://home"
            addressText = "litedirt://home"
            pageResult = null
            isLoading = false
            return
        }

        val target = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://lite.duckduckgo.com/lite/?q=${Uri.encode(trimmed)}"
        }

        if (pushHistory && currentUrl != target) {
            backStack.add(currentUrl)
            forwardStack.clear()
        }
        currentUrl = target
        addressText = target
        isLoading = true

        scope.launch {
            val outcome = fetchWebPageOrDownload(target)
            isLoading = false
            when (outcome) {
                is FetchOutcome.DownloadTriggered -> {
                    onDownloadFile(outcome.url, outcome.contentDisposition, outcome.mimeType)
                    pageResult = WebPageResult(
                        url = outcome.url,
                        title = "Download Started",
                        textSnippet = "File is downloading directly into your LiteDirt Vault (/Vault/APKs/).",
                        links = emptyList()
                    )
                }
                is FetchOutcome.PageLoaded -> {
                    currentUrl = outcome.page.url
                    addressText = outcome.page.url
                    pageResult = outcome.page
                }
            }
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && initialUrl != "litedirt://home") {
            loadTarget(initialUrl, pushHistory = false)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp),
        border = if (isFullscreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Browser Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B111E))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        if (backStack.isNotEmpty()) {
                            val prev = backStack.removeAt(backStack.lastIndex)
                            forwardStack.add(currentUrl)
                            loadTarget(prev, pushHistory = false)
                        }
                    },
                    enabled = backStack.isNotEmpty(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = if (backStack.isNotEmpty()) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (forwardStack.isNotEmpty()) {
                            val next = forwardStack.removeAt(forwardStack.lastIndex)
                            backStack.add(currentUrl)
                            loadTarget(next, pushHistory = false)
                        }
                    },
                    enabled = forwardStack.isNotEmpty(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        "Forward",
                        tint = if (forwardStack.isNotEmpty()) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { loadTarget(currentUrl, pushHistory = false) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Reload", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }

                Surface(
                    color = Color(0xFF172133),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { loadTarget(addressText) }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vm_browser_url_input")
                        )
                    }
                }

                IconButton(
                    onClick = { loadTarget("litedirt://home") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Home, "Browser Home", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("vm_browser_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.Close else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen Browser",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onCloseBrowser != null && !isFullscreen) {
                    IconButton(
                        onClick = onCloseBrowser,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close Browser", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Quick Bookmarks Strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1626))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(QUICK_BOOKMARKS) { bm ->
                    AssistChip(
                        onClick = { loadTarget(bm.url) },
                        label = { Text(bm.title, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Browser Content Area (Pure Compose — zero Chromium rendernode errors)
            val currentResult = pageResult
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF090D16)),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentUrl == "litedirt://home" || currentResult == null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "ANDROID 10 VM BROWSER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "LiteDirt Web & APK Downloader",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Browse pages and tap any .apk download link to save straight into your LiteDirt Vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    items(QUICK_BOOKMARKS) { bm ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF2B384F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { loadTarget(bm.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text(bm.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF2B384F))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = currentResult.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = currentResult.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentResult.error != null) {
                                    Text(
                                        text = currentResult.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (currentResult.textSnippet.isNotBlank()) {
                                    Text(
                                        text = currentResult.textSnippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentResult.url)))
                                        } catch (_: Exception) {
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open in System Browser")
                                }
                            }
                        }
                    }

                    if (currentResult.links.isNotEmpty()) {
                        item {
                            Text(
                                text = "Detected Links & Downloads (${currentResult.links.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        items(currentResult.links) { link ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (link.isDownloadable) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                                    else Color(0xFF2B384F)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (link.isDownloadable) {
                                            onDownloadFile(link.url, null, null)
                                        } else {
                                            loadTarget(link.url)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (link.isDownloadable) Icons.Default.Download else Icons.Default.Link,
                                        contentDescription = null,
                                        tint = if (link.isDownloadable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = link.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = link.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface FetchOutcome {
    data class DownloadTriggered(
        val url: String,
        val contentDisposition: String?,
        val mimeType: String?
    ) : FetchOutcome

    data class PageLoaded(val page: WebPageResult) : FetchOutcome
}

private suspend fun fetchWebPageOrDownload(targetUrl: String): FetchOutcome =
    withContext(Dispatchers.IO) {
        if (targetUrl.endsWith(".apk", ignoreCase = true) ||
            targetUrl.endsWith(".zip", ignoreCase = true)
        ) {
            return@withContext FetchOutcome.DownloadTriggered(targetUrl, null, null)
        }

        try {
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; LiteDirt) AppleWebKit/537.36 Mobile Safari/537.36"
                )
            }
            conn.connect()
            val finalUrl = conn.url.toString()
            val contentType = conn.contentType ?: "text/html"
            val disposition = conn.getHeaderField("Content-Disposition")

            if (contentType.contains("android.package-archive", ignoreCase = true) ||
                contentType.contains("application/zip", ignoreCase = true) ||
                contentType.contains("application/octet-stream", ignoreCase = true) ||
                (disposition?.contains(".apk", ignoreCase = true) == true)
            ) {
                conn.disconnect()
                return@withContext FetchOutcome.DownloadTriggered(finalUrl, disposition, contentType)
            }

            val html = conn.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(128 * 1024)
                val read = reader.read(buf)
                if (read > 0) String(buf, 0, read) else ""
            }
            conn.disconnect()

            val titleMatch = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)
            val title = titleMatch?.groupValues?.getOrNull(1)
                ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString().trim() }
                ?.ifBlank { finalUrl }
                ?: finalUrl

            val cleanBody = html
                .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            val plainText = Html.fromHtml(cleanBody, Html.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(700)

            val linkRegex = Regex(
                "<a\\s+[^>]*href=[\"']([^\"'#]+)[\"'][^>]*>(.*?)</a>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val baseUri = URL(finalUrl)
            val extractedLinks = linkRegex.findAll(html)
                .mapNotNull { m ->
                    val rawHref = m.groupValues[1].trim()
                    if (rawHref.startsWith("javascript:", ignoreCase = true)) return@mapNotNull null
                    val resolved = try {
                        URL(baseUri, rawHref).toString()
                    } catch (_: Exception) {
                        null
                    } ?: return@mapNotNull null
                    val label = Html.fromHtml(m.groupValues[2], Html.FROM_HTML_MODE_COMPACT)
                        .toString()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .ifBlank { resolved }
                    val isDl = resolved.endsWith(".apk", ignoreCase = true) ||
                        resolved.endsWith(".zip", ignoreCase = true) ||
                        label.contains("Download APK", ignoreCase = true)
                    ExtractedWebLink(label = label, url = resolved, isDownloadable = isDl)
                }
                .distinctBy { it.url }
                .take(25)
                .toList()

            FetchOutcome.PageLoaded(
                WebPageResult(
                    url = finalUrl,
                    title = title,
                    textSnippet = plainText,
                    links = extractedLinks
                )
            )
        } catch (e: Exception) {
            FetchOutcome.PageLoaded(
                WebPageResult(
                    url = targetUrl,
                    title = "Connection Error",
                    textSnippet = "",
                    links = emptyList(),
                    error = "Could not load page: ${e.message ?: "Network error"}"
                )
            )
        }
    }
