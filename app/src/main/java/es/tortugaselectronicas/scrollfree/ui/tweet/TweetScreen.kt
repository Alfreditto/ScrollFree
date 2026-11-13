package es.tortugaselectronicas.scrollfree.ui.tweet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import es.tortugaselectronicas.scrollfree.data.MediaKind
import es.tortugaselectronicas.scrollfree.ui.media.MediaViewerDialog

/**
 * Shows a tweet: author (name + @handle), full text with clickable links, and its media
 * (images/videos) in a vertical cascade. Tapping media opens it full-screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweetScreen(
    tweet: MediaKind.Tweet,
    onBack: () -> Unit,
    onOpenVideo: (java.io.File) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Tweet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TweetHeader(
                    displayName = tweet.displayName,
                    handle = tweet.handle,
                    text = tweet.text
                )
            }
            itemsIndexed(tweet.items) { index, item ->
                if (item.isVideo) {
                    VideoTile(file = item.file, onClick = { onOpenVideo(item.file) })
                } else {
                    ImageTile(
                        file = item.file,
                        onClick = { viewerIndex = index }
                    )
                }
            }
        }
    }

    // Full-screen image viewer.
    val index = viewerIndex
    if (index != null) {
        val imageItems = tweet.items.filter { !it.isVideo }
        val start = tweet.items.take(index).count { !it.isVideo }.coerceIn(0, (imageItems.size - 1).coerceAtLeast(0))
        MediaViewerDialog(
            images = imageItems.map { it.file },
            startIndex = start,
            onDismiss = { viewerIndex = null }
        )
    }
}

@Composable
private fun TweetHeader(displayName: String?, handle: String?, text: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Simple avatar placeholder circle.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (displayName ?: handle ?: "?").take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(12.dp))
            Column {
                if (!displayName.isNullOrBlank()) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!handle.isNullOrBlank()) {
                    Text(
                        text = "@$handle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!text.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            TweetText(text = text)
        }
    }
}

/** Renders the tweet text with any http(s) URLs underlined and clickable. */
@Composable
fun TweetText(text: String) {
    val context = LocalContext.current
    val urlRegex = remember { Regex("""https?://\S+""") }

    val annotated = buildAnnotatedString {
        var lastIndex = 0
        urlRegex.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))
            val url = match.value.trimEnd('.', ',', ')', ']', '}')
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
            ) {
                // pushStringAnnotation enables click handling below.
                pushStringAnnotation(tag = "URL", annotation = url)
                append(url)
                pop()
            }
            // Any trailing punctuation trimmed from the URL is appended as plain text.
            append(match.value.substring(url.length))
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }

    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { annotation ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(annotation.item)
                            )
                        )
                    }
                }
        }
    )
}

@Composable
private fun ImageTile(file: java.io.File, onClick: () -> Unit) {
    AsyncImage(
        model = file,
        contentDescription = "Imagen del tweet",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    )
}

@Composable
private fun VideoTile(file: java.io.File, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Reproducir vídeo",
            tint = Color.White,
            modifier = Modifier.size(56.dp)
        )
    }
}
