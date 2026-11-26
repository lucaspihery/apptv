package io.github.lucaspihery.apptv.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.Program
import io.github.lucaspihery.apptv.core.data.live.VirtualCategories
import io.github.lucaspihery.apptv.core.designsystem.theme.ApptvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Aperçus des écrans à la taille d'une TV 1080p (960 × 540 dp), rendus sur le PC.
 * Images écrites dans `feature/live/build/previews/`. Données fictives, pas de réseau.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenPreviewsTest {
    @get:Rule val compose = createComposeRule()

    private val now = System.currentTimeMillis() / 1000
    private val slot = now / 1800 * 1800

    private val names = listOf(
        "France Info", "Arte HD", "Sport Live 1", "Cinéma Premier", "Documentaires",
        "Musique Hits", "Jeunesse", "Météo", "Séries Plus", "Histoire", "Cuisine TV", "Voyage",
    )
    private val channels = names.mapIndexed { i, n -> LiveChannel(n, i + 1L, null, "c", null, 100L + i, null) }

    private val titles = listOf(
        "Journal", "Le grand débat", "Match en direct", "Film : La traversée", "Planète océans",
        "Top 50", "Dessins animés", "Bulletin météo", "Épisode 4", "Les grandes batailles", "Recettes du soir", "Carnets de route",
    )

    private val epg = object : EpgRepository {
        override fun cached(channel: LiveChannel, minCount: Int): List<Program> {
            val i = (channel.streamId ?: 100L).toInt() - 100
            val offset = (i * 7 % 5) * 600L
            return (0 until 8).map { k ->
                val start = slot - 1800 + offset + k * 2700L
                Program(
                    title = if (k == 0) titles[i % titles.size] else "${titles[(i + k) % titles.size]} (${k + 1})",
                    description = "Résumé du programme : présentation, invités et reportages.",
                    start = start,
                    end = start + 2700L,
                )
            }
        }

        override suspend fun programs(channel: LiveChannel, minCount: Int) = cached(channel, minCount)
    }

    private val actions = ChannelsActions({}, {}, { 0 }, {}, {}, {}, {}, {}, {})

    private fun state(selected: String? = null, list: List<LiveChannel> = channels, query: String = "") = ChannelsUiState(
        loading = false,
        categories = listOf(
            CategoryItem(VirtualCategories.FAVORITES, FAVORITES_NAME, 3),
            CategoryItem(null, ALL, 28_844),
            CategoryItem("c", "France | TNT", 42),
            CategoryItem("s", "Sport", 116),
            CategoryItem("m", "Cinéma", 230),
            CategoryItem("d", "Documentaires", 58),
            CategoryItem("k", "Jeunesse", 31),
        ),
        selectedCategoryId = selected,
        channels = list,
        favorites = setOf(channels[0].key, channels[2].key, channels[4].key),
        query = query,
        hasGuide = true,
    )

    private fun shoot(name: String) = compose.onRoot().captureRoboImage(
        "build/previews/$name.png",
        roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    @Test
    fun liste_des_chaines() {
        compose.setContent { ApptvTheme { ChannelsScreen(state(), epg, actions) } }
        shoot("1-liste-des-chaines")
    }

    @Test
    fun mes_chaines() {
        val favs = listOf(channels[0], channels[2], channels[4])
        compose.setContent { ApptvTheme { ChannelsScreen(state(VirtualCategories.FAVORITES, favs), epg, actions) } }
        shoot("2-mes-chaines")
    }

    @Test
    fun recherche() {
        val found = channels.filter { it.name.contains("o", ignoreCase = true) }.take(4)
        compose.setContent { ApptvTheme { ChannelsScreen(state(SEARCH, found, "o"), epg, actions) } }
        shoot("3-recherche")
    }

    @Test
    fun guide_tv() {
        compose.setContent {
            ApptvTheme { GuideScreen(GuideUiState(loading = false, title = "France | TNT", channels = channels), epg) {} }
        }
        shoot("4-guide-tv")
    }

    @Test
    fun bandeau_de_zapping() {
        val channel = channels[1]
        compose.setContent {
            ApptvTheme {
                // Fond gris à la place de la vidéo.
                Box(Modifier.fillMaxSize().background(Color(0xFF2A2F3A)), contentAlignment = Alignment.BottomCenter) {
                    PlayerBanner(
                        PlayerUiState(
                            channel = channel,
                            position = 2,
                            total = 42,
                            buffering = false,
                            programs = epg.cached(channel, 2),
                        ),
                    )
                }
            }
        }
        shoot("5-bandeau-de-zapping")
    }
}
