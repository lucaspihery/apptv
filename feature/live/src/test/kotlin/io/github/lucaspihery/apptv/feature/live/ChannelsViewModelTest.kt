package io.github.lucaspihery.apptv.feature.live

import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.live.LiveCatalog
import io.github.lucaspihery.apptv.core.data.live.LiveCategory
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.LiveRepository
import io.github.lucaspihery.apptv.core.testing.FakeAccountRepository
import io.github.lucaspihery.apptv.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.FavoritesRepository
import io.github.lucaspihery.apptv.core.data.live.Program
import io.github.lucaspihery.apptv.core.data.live.VirtualCategories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun channel(name: String, cat: String?) = LiveChannel(name, null, null, cat, null, name.hashCode().toLong(), null)

    private class FakeLive(var result: CoreResult<LiveCatalog>) : LiveRepository {
        var loads = 0
        override suspend fun catalog(refresh: Boolean): CoreResult<LiveCatalog> = result.also { loads++ }
        override suspend fun streamUrl(channel: LiveChannel): String? = null
    }

    private val catalog = LiveCatalog(
        categories = listOf(LiveCategory("tnt", "TNT"), LiveCategory("sport", "Sport")),
        channels = listOf(channel("TF1", "tnt"), channel("France 2", "tnt"), channel("Foot", "sport")),
    )

    private class FakeFavorites : FavoritesRepository {
        override val favorites = MutableStateFlow<Set<String>>(emptySet())
        override suspend fun toggle(channel: LiveChannel) {
            favorites.value = favorites.value.let { if (channel.key in it) it - channel.key else it + channel.key }
        }
    }

    private object NoEpg : EpgRepository {
        override fun cached(channel: LiveChannel, minCount: Int): List<Program>? = null
        override suspend fun programs(channel: LiveChannel, minCount: Int): List<Program> = emptyList()
    }

    private val favorites = FakeFavorites()

    private fun vm(live: LiveRepository) =
        ChannelsViewModel(live, FakeAccountRepository(), favorites, NoEpg, main.dispatcher, settleMs = 250)

    @Test
    fun `recherche sans accents ni casse`() = runTest(main.dispatcher) {
        val vm = vm(FakeLive(CoreResult.Ok(catalog)))
        vm.selectCategory(SEARCH)
        vm.setQuery("FRANCE  2")
        advanceTimeBy(300)
        assertEquals(listOf("France 2"), vm.state.value.channels.map { it.name })
        vm.setQuery("fôot")
        advanceTimeBy(300)
        assertEquals(listOf("Foot"), vm.state.value.channels.map { it.name })
        // Lecture d'un résultat : on zappe ensuite dans toutes les chaînes.
        assertEquals(null to 2, vm.playTarget(0))
    }

    @Test
    fun `mes chaines suit les favoris`() = runTest(main.dispatcher) {
        val vm = vm(FakeLive(CoreResult.Ok(catalog)))
        assertEquals(VirtualCategories.FAVORITES, vm.state.value.categories.first().id)
        assertEquals(0, vm.state.value.categories.first().count)
        vm.selectCategory(VirtualCategories.FAVORITES)
        assertTrue(vm.state.value.channels.isEmpty())
        vm.toggleFavorite(catalog.channels[2])
        assertEquals(listOf("Foot"), vm.state.value.channels.map { it.name })
        assertEquals(1, vm.state.value.categories.first().count)
        assertTrue(catalog.channels[2].key in vm.state.value.favorites)
    }

    @Test
    fun `parcourir les catégories ne change la liste qu'une fois le focus posé`() = runTest(main.dispatcher) {
        val vm = vm(FakeLive(CoreResult.Ok(catalog)))
        vm.focusCategory("tnt")
        vm.focusCategory("sport")
        advanceTimeBy(100)
        assertEquals(null, vm.state.value.selectedCategoryId)
        advanceTimeBy(200)
        assertEquals("sport", vm.state.value.selectedCategoryId)
        assertEquals(listOf("Foot"), vm.state.value.channels.map { it.name })
    }

    @Test
    fun `retour du lecteur sur la dernière chaîne ouverte`() {
        val vm = vm(FakeLive(CoreResult.Ok(catalog)))
        assertEquals(0, vm.lastPlayedIndex("tnt"))
        vm.selectCategory("tnt")
        assertEquals("tnt" to 1, vm.playTarget(1))
        assertEquals(1, vm.lastPlayedIndex("tnt"))
        assertEquals(0, vm.lastPlayedIndex(null))
    }

    @Test
    fun `arrivée sur toutes les chaînes puis filtre par catégorie`() {
        val vm = vm(FakeLive(CoreResult.Ok(catalog)))
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf(VirtualCategories.FAVORITES, null, "tnt", "sport"), s.categories.map { it.id })
        assertEquals(listOf(0, 3, 2, 1), s.categories.map { it.count })
        assertEquals(3, s.channels.size)
        assertEquals(ALL, s.selectedCategoryName)

        vm.selectCategory("sport")
        assertEquals(listOf("Foot"), vm.state.value.channels.map { it.name })
        assertEquals("Sport", vm.state.value.selectedCategoryName)
    }

    @Test
    fun `erreur puis nouvel essai`() {
        val live = FakeLive(CoreResult.Err(AccountError.Timeout))
        val vm = vm(live)
        assertEquals(AccountError.Timeout, vm.state.value.error)
        live.result = CoreResult.Ok(catalog)
        vm.load()
        assertEquals(null, vm.state.value.error)
        assertEquals(2, live.loads)
    }
}
