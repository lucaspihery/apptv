package io.github.lucaspihery.apptv.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.Program
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Heure courante en secondes, rafraîchie toutes les 30 s (barres d'avancement). */
@Composable
fun rememberNowSeconds(): State<Long> = produceState(System.currentTimeMillis() / 1000) {
    while (true) {
        delay(30_000)
        value = System.currentTimeMillis() / 1000
    }
}

/**
 * Programmes d'une chaîne : cache immédiat, sinon demande au serveur après un court délai,
 * pour ne rien demander pendant un défilement rapide (la composition quitte la ligne avant).
 */
@Composable
fun rememberPrograms(epg: EpgRepository, channel: LiveChannel, minCount: Int = EpgRepository.SHORT): State<List<Program>?> =
    produceState(initialValue = epg.cached(channel, minCount), channel, minCount) {
        if (value == null && channel.streamId != null) {
            delay(EPG_SETTLE_MS)
            value = epg.programs(channel, minCount)
        }
    }

private const val EPG_SETTLE_MS = 180L

private val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)

fun formatTime(seconds: Long): String = synchronized(timeFormat) { timeFormat.format(Date(seconds * 1000)) }

/** Logo à la taille d'affichage (mémoire limitée), ou initiales si absent. */
@Composable
fun ChannelLogo(channel: LiveChannel, width: Dp = 64.dp, height: Dp = 40.dp) {
    val p = Glass.palette
    Box(
        Modifier.size(width, height).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        val logo = channel.logo
        if (logo == null) {
            Text(
                initials(channel.name),
                style = MaterialTheme.typography.labelMedium,
                color = p.contentMuted,
                maxLines = 1,
            )
        } else {
            val context = LocalPlatformContext.current
            val density = LocalDensity.current
            val request = remember(logo) {
                with(density) {
                    ImageRequest.Builder(context).data(logo).size(width.roundToPx(), height.roundToPx()).build()
                }
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width, height),
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ', '|', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()

/** Ligne « programme en cours » : titre + barre d'avancement. */
@Composable
fun NowLine(program: Program, now: Long, modifier: Modifier = Modifier) {
    val p = Glass.palette
    androidx.compose.foundation.layout.Column(modifier) {
        Text(
            program.title,
            style = MaterialTheme.typography.bodyMedium,
            color = p.content.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ProgressBar(program.progress(now), Modifier.fillMaxWidth().height(3.dp))
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val accent = Glass.palette.accent.color
    Box(modifier.clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(accent))
    }
}
