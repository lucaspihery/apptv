package io.github.lucaspihery.apptv.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.lucaspihery.apptv.core.data.live.StreamQuality

private val BadgeShape = RoundedCornerShape(5.dp)
private val Gold = Color(0xFFFFC857)
private val Blue = Color(0xFF6FA4FF)
private val Mint = Color(0xFF2ED3A0)
private val Grey = Color(0xFFB8BED6)

/**
 * Pastilles de qualité : définition (4K, QHD, FHD, HD, SD) et images par seconde.
 * Simples aplats colorés (aucune ombre) : peu coûteux, même dans une longue liste.
 */
@Composable
fun QualityBadges(quality: StreamQuality?, modifier: Modifier = Modifier) {
    if (quality == null) return
    val resolution = quality.resolutionLabel
    val fps = quality.fpsLabel
    if (resolution == null && fps == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (resolution != null) {
            val color = when {
                quality.height >= 2000 -> Gold
                quality.height >= 1000 -> Blue
                quality.height >= 700 -> Color.White
                else -> Grey
            }
            Badge(resolution, color)
        }
        if (fps != null) Badge(fps, if ((quality.fps ?: 0) >= 48) Mint else Grey)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), BadgeShape)
            .border(1.dp, color.copy(alpha = 0.55f), BadgeShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
