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
import org.junit.Rule
import org.junit.Test

class ChannelsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun channel(name: String, cat: String?) = LiveChannel(name, null, null, cat, null, 1, null)

    private class FakeLive(var result: CoreResult<LiveCatalog>) : LiveRepository {
        var loads = 0
        override suspend fun catalog(refresh: Boolean): CoreResult<LiveCatalog> = result.also { loads++ }
        override suspend fun streamUrl(channel: LiveChannel): String? = null
    }

    private val catalog = LiveCatalog(
        categories = listOf(LiveCategory("tnt", "TNT"), LiveCategory("sport", "Sport")),
        channels = listOf(channel("TF1", "tnt"), channel("France 2", "tnt"), channel("Foot", "sport")),
    )

    private fun vm(live: LiveRepository) =
        ChannelsViewModel(live, FakeAccountRepository(), main.dispatcher, settleMs = 250)

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
        vm.onPlayed("tnt", 1)
        assertEquals(1, vm.lastPlayedIndex("tnt"))
        assertEquals(0, vm.lastPlayedIndex(null))
    }

    @Test
    fun `arrivée sur toutes les chaînes puis filtre par catégorie`() {
        val vm = vm(FakeLive(CoreResult.Ok(catalog)))
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf(null, "tnt", "sport"), s.categories.map { it.id })
        assertEquals(listOf(3, 2, 1), s.categories.map { it.count })
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
