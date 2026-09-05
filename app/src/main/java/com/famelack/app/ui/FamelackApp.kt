package com.famelack.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.famelack.app.data.Channel
import com.famelack.app.data.MediaKind
import com.famelack.app.player.PlayerHolder
import com.famelack.app.ui.components.MiniPlayerBar
import com.famelack.app.ui.screens.BrowseScreen
import com.famelack.app.ui.screens.FavoritesScreen
import com.famelack.app.ui.screens.PlayerScreen
import com.famelack.app.ui.screens.PlayerTabScreen

enum class TopTab(val kind: MediaKind?, val label: String) {
    TV(MediaKind.TV, "TV"),
    RADIO(MediaKind.RADIO, "Radio"),
    WEBCAM(MediaKind.WEBCAM, "Webcam"),
    FAVORITES(null, "Favorites"),
    PLAYER(null, "Player")
}

@Composable
fun FamelackApp() {
    var currentTab by remember { mutableStateOf(TopTab.TV) }
    var playerChannel by remember { mutableStateOf<Channel?>(null) }
    val activeChannel by PlayerHolder.currentChannelFlow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Show floating mini-player bar when stream is playing and not on full player
                if (activeChannel != null && playerChannel == null && currentTab != TopTab.PLAYER) {
                    MiniPlayerBar(
                        channel = activeChannel!!,
                        onClick = { playerChannel = activeChannel },
                        onClose = { PlayerHolder.stop() }
                    )
                }

                BottomBar(currentTab) { selected ->
                    currentTab = selected
                    if (selected == TopTab.PLAYER && playerChannel != null) {
                        playerChannel = null // Seamlessly transition to embedded PlayerTab
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                TopTab.FAVORITES -> FavoritesScreen(onPlay = {
                    PlayerHolder.setCurrentChannel(it)
                    playerChannel = it
                })
                TopTab.PLAYER -> PlayerTabScreen(onPlayChannel = {
                    PlayerHolder.setCurrentChannel(it)
                    playerChannel = it
                })
                else -> {
                    val kind = currentTab.kind!!
                    BrowseScreen(
                        kind = kind,
                        onPlay = {
                            PlayerHolder.setCurrentChannel(it)
                            playerChannel = it
                        }
                    )
                }
            }
        }
    }

    // Modal overlay for full screen experience
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
                            TopTab.PLAYER -> Icons.Filled.PlayCircle
                        },
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}
