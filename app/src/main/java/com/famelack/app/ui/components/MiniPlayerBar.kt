package com.famelack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famelack.app.data.Channel
import com.famelack.app.data.codeToFlag
import com.famelack.app.player.PlayerHolder

@Composable
fun MiniPlayerBar(
    channel: Channel,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val isPlaying by PlayerHolder.isPlayingFlow.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flag or Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1014)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (channel.isYoutube) "YT" else codeToFlag(channel.country.uppercase()),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (channel.isYoutube) Color.Red else Color.White
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isPlaying) Color(0xFF00E676) else Color.Gray)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "LIVE" else "PAUSED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) Color(0xFF00E676) else Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = PlayerHolder.currentQuality.label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Play / Pause Toggle
            IconButton(
                onClick = { PlayerHolder.togglePlayPause() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Toggle Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Close
            IconButton(
                onClick = {
                    PlayerHolder.stop()
                    onClose()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
