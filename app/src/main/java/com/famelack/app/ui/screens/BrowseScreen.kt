package com.famelack.app.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famelack.app.FamelackApp
import com.famelack.app.data.Channel
import com.famelack.app.data.CountryInfo
import com.famelack.app.data.MediaKind
import com.famelack.app.data.codeToFlag
import com.famelack.app.player.PlayerHolder
import com.famelack.app.ui.components.ChannelRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    kind: MediaKind,
    onPlay: (Channel) -> Unit
) {
    val repo = FamelackApp.instance.repository
    var loaded by remember { mutableStateOf(repo.let { false }) }
    var countries by remember { mutableStateOf<List<CountryInfo>>(emptyList()) }
    var selectedCountry by remember { mutableStateOf<CountryInfo?>(null) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Channel>>(emptyList()) }

    LaunchedEffect(kind) {
        repo.ensureLoaded()
        countries = repo.countriesFor(kind)
        loaded = true
    }

    LaunchedEffect(selectedCountry, kind) {
        if (selectedCountry != null) {
            channels = repo.channelsByCountry(kind, selectedCountry!!.code)
        } else {
            channels = emptyList()
        }
    }

    LaunchedEffect(searchQuery, kind) {
        searchResults = if (searchQuery.isBlank()) emptyList()
        else repo.search(kind, searchQuery, limit = 300)
    }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Search ${kind.slug}…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (searchQuery.isNotBlank()) {
            // Search results
            if (searchResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results for \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(searchResults, key = { "s-${it.id}" }) { ch ->
                        ChannelRow(
                            channel = ch,
                            onClick = {
                                if (ch.isYoutubeOnly) {
                                    onPlay(ch)
                                } else {
                                    val url = ch.primaryUrl ?: return@ChannelRow
                                    PlayerHolder.playStream(
                                        context = FamelackApp.instance,
                                        url = url,
                                        title = ch.name,
                                        channel = ch
                                    )
                                    onPlay(ch)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
            return@Column
        }

        if (selectedCountry == null) {
            // Country picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select a Country",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${countries.count { it.hasChannels }} countries • ${repo.counts(kind)} channels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = {
                        val ch = repo.randomChannel(kind)
                        if (ch != null) {
                            if (ch.isYoutubeOnly) {
                                onPlay(ch)
                            } else {
                                val url = ch.primaryUrl
                                if (url != null) {
                                    PlayerHolder.playStream(
                                        context = FamelackApp.instance,
                                        url = url,
                                        title = ch.name,
                                        channel = ch
                                    )
                                }
                                onPlay(ch)
                            }
                        }
                    },
                    label = { Text("Random") },
                    leadingIcon = { Icon(Icons.Filled.Casino, null) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(countries, key = { it.code }) { c ->
                    CountryRow(
                        info = c,
                        onClick = { selectedCountry = c }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        } else {
            // Channels of selected country
            val country = selectedCountry!!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedCountry = null }) {
                    Icon(Icons.Filled.Public, contentDescription = "Back to countries")
                }
                Text(
                    text = "${country.flagEmoji}  ${country.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${channels.size} channels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(channels, key = { "c-${it.id}" }) { ch ->
                        ChannelRow(
                            channel = ch,
                            onClick = {
                                if (ch.isYoutubeOnly) {
                                    onPlay(ch)
                                } else {
                                    val url = ch.primaryUrl ?: return@ChannelRow
                                    PlayerHolder.playStream(
                                        context = FamelackApp.instance,
                                        url = url,
                                        title = ch.name,
                                        channel = ch
                                    )
                                    onPlay(ch)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(info: CountryInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(info.flagEmoji, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (info.capital != null) {
                Text(
                    info.capital,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (info.hasChannels) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${info.channelCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
