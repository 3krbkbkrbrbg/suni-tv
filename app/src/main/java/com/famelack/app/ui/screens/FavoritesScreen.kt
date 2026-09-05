package com.famelack.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.famelack.app.FamelackApp
import com.famelack.app.data.Channel
import com.famelack.app.data.MediaKind
import com.famelack.app.player.PlayerHolder
import com.famelack.app.ui.components.ChannelRow
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(onPlay: (Channel) -> Unit) {
    val app = FamelackApp.instance
    val repo = app.repository
    val scope = rememberCoroutineScope()
    val favIds by app.favoritesStore.ids.collectAsState(initial = emptySet())
    var loaded by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }

    LaunchedEffect(Unit) {
        repo.ensureLoaded()
        loaded = true
    }

    LaunchedEffect(favIds, loaded) {
        if (!loaded) return@LaunchedEffect
        // Resolve every favorite id by scanning all 3 kinds × all countries.
        // In-memory JSONObject lookup is fast; for 167+218+96 countries
        // we process ≈ 37k channels in well under 1s.
        val out = ArrayList<Channel>(favIds.size)
        for (kind in MediaKind.entries) {
            val countries = repo.countriesFor(kind)
            for (c in countries) {
                if (c.channelCount == 0) continue
                val list = repo.channelsByCountry(kind, c.code)
                for (ch in list) if (ch.id in favIds) out.add(ch)
            }
        }
        channels = out
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Favorites",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!loaded) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        "No favorites yet.\nTap the ♥ on any channel to add it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(channels, key = { "fav-${it.id}" }) { ch ->
                    ChannelRow(
                        channel = ch,
                        isFavorite = true,
                        onClick = {
                            if (ch.isYoutube) {
                                onPlay(ch)
                            } else {
                                val url = ch.primaryUrl ?: return@ChannelRow
                                PlayerHolder.playStream(
                                    context = app,
                                    url = url,
                                    title = ch.name,
                                    channel = ch
                                )
                                onPlay(ch)
                            }
                        },
                        onFavoriteToggle = {
                            scope.launch { app.favoritesStore.toggle(ch.id) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
