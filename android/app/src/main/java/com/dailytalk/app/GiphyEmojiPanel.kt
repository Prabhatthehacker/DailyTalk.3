package com.dailytalk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** App-owned emoji-style picker, populated from GIPHY's REST emoji endpoint. */
@Composable
fun GiphyEmojiPanel(onPick: (Gif) -> Unit) {
    var emojis by remember { mutableStateOf<List<Gif>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching { Giphy.emojis() }
            .onSuccess { emojis = it }
            .onFailure { error = "Could not load GIPHY emojis. Check the API key and connection, then try again." }
        loading = false
    }

    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).background(Palette.Ivory).padding(8.dp)) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp))
            error != null -> Text(error!!, color = Palette.Danger)
            emojis.isEmpty() -> Text("No emoji media was returned by GIPHY.", color = Palette.Muted)
            else -> LazyVerticalGrid(
                GridCells.Adaptive(48.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(emojis, key = { it.id }) { emoji ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(emoji.preview).crossfade(true).build(),
                        contentDescription = "Animated emoji",
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(emoji) },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
