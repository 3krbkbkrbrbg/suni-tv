package com.famelack.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.famelack.app.data.MediaKind
import com.famelack.app.ui.screens.BrowseScreen
import com.famelack.app.ui.screens.FavoritesScreen
import com.famelack.app.ui.screens.PlayerScreen

enum class TopTab(val kind: MediaKind?, val label: String) {
    TV(MediaKind.TV, "TV"),
    RADIO(MediaKind.RADIO, "Radio"),
    WEBCAM(MediaKind.WEBCAM, "Webcam"),
    FAVORITES(null, "Favorites")
}

@Composable
fun FamelackApp() {
    var currentTab by remember { mutableStateOf(TopTab.TV) }
    var playerChannel by remember { mutableStateOf<com.famelack.app.data.Channel?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { BottomBar(currentTab) { currentTab = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                TopTab.FAVORITES -> FavoritesScreen(onPlay = { playerChannel = it })
                else -> {
                    val kind = currentTab.kind!!
                    BrowseScreen(
                        kind = kind,
                        onPlay = { playerChannel = it }
                    )
                }
            }
        }
    }

    playerChannel?.let { ch ->
        PlayerScreen(channel = ch, onClose = { playerChannel = null })
    }
}

@Composable
private fun BottomBar(current: TopTab, onSelect: (TopTab) -> Unit) {
    NavigationBar {
        TopTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            TopTab.TV -> Icons.Filled.LiveTv
                            TopTab.RADIO -> Icons.Filled.Radio
                            TopTab.WEBCAM -> Icons.Filled.Videocam
                            TopTab.FAVORITES -> Icons.Filled.Favorite
                        },
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}
