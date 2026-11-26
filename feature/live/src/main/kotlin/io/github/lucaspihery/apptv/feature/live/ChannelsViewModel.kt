package io.github.lucaspihery.apptv.feature.live

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.account.AccountType
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.FavoritesRepository
import io.github.lucaspihery.apptv.core.data.live.LiveCatalog
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.LiveRepository
import io.github.lucaspihery.apptv.core.data.live.VirtualCategories
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.inject.Inject

/** Entrée de la colonne de gauche : `id == null` pour « Toutes les chaînes ». */
@Immutable
data class CategoryItem(val id: String?, val name: String, val count: Int)

@Immutable
data class ChannelsUiState(
    val loading: Boolean = true,
    val error: AccountError? = null,
    val categories: List<CategoryItem> = emptyList(),
    /** `null` = toutes, [VirtualCategories.FAVORITES], [SEARCH] ou un identifiant de catégorie. */
    val selectedCategoryId: String? = null,
    val channels: List<LiveChannel> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val query: String = "",
    /** Le guide n'existe que pour les comptes Xtream. */
    val hasGuide: Boolean = false,
) {
    val selectedCategoryName: String
        get() = when (selectedCategoryId) {
            SEARCH -> "Recherche"
            else -> categories.firstOrNull { it.id == selectedCategoryId }?.name ?: ALL
        }
}

internal const val ALL = "Toutes les chaînes"
internal const val FAVORITES_NAME = "Mes chaînes"
internal const val SEARCH = "@search"

/**
 * Fluidité sur une TV modeste avec des dizaines de milliers de chaînes :
 * - l'index « catégorie → chaînes » est calculé une seule fois, hors du thread UI ;
 * - parcourir les catégories au D-pad ne remplace la liste qu'une fois le focus posé ;
 * - la recherche tourne hors du thread UI, sur des noms normalisés calculés une fois.
 */
@HiltViewModel
class ChannelsViewModel internal constructor(
    private val live: LiveRepository,
    private val accounts: AccountRepository,
    private val favoritesRepo: FavoritesRepository,
    val epg: EpgRepository,
    private val compute: CoroutineDispatcher,
    private val settleMs: Long,
) : ViewModel() {

    @Inject constructor(
        live: LiveRepository,
        accounts: AccountRepository,
        favorites: FavoritesRepository,
        epg: EpgRepository,
    ) : this(live, accounts, favorites, epg, Dispatchers.Default, CATEGORY_SETTLE_MS)

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()
    private var catalog: LiveCatalog? = null
    private var byCategory: Map<String?, List<LiveChannel>> = emptyMap()
    private var baseItems: List<CategoryItem> = emptyList()
    private var normalizedNames: Array<String>? = null
    private var pendingSelection: Job? = null
    private var searchJob: Job? = null
    private var searchResults: List<LiveChannel> = emptyList()
    private var favoriteChannels: List<LiveChannel> = emptyList()

    /** Dernière chaîne ouverte par catégorie, pour y revenir au retour du lecteur. */
    private val lastPlayed = HashMap<String?, Int>()

    init {
        load(refresh = false)
        viewModelScope.launch {
            favoritesRepo.favorites.collect { favs -> onFavoritesChanged(favs) }
        }
    }

    fun load(refresh: Boolean = true) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val hasGuide = accounts.account.first()?.type == AccountType.XTREAM
            when (val r = live.catalog(refresh)) {
                is CoreResult.Err -> _state.update { it.copy(loading = false, error = r.error) }
                is CoreResult.Ok -> {
                    val (index, items) = withContext(compute) { buildIndex(r.value) }
                    catalog = r.value
                    byCategory = index
                    baseItems = items
                    normalizedNames = null
                    favoriteChannels = withContext(compute) { r.value.playlist(VirtualCategories.FAVORITES, _state.value.favorites) }
                    _state.update {
                        val selected = it.selectedCategoryId?.takeIf { id -> id in index || id == SEARCH || id == VirtualCategories.FAVORITES }
                        it.copy(
                            loading = false,
                            hasGuide = hasGuide,
                            categories = withFavorites(items, favoriteChannels.size),
                            selectedCategoryId = selected,
                            channels = channelsFor(selected),
                        )
                    }
                }
            }
        }
    }

    /** Focus posé sur une catégorie : la liste suit après un court délai. */
    fun focusCategory(id: String?) {
        pendingSelection?.cancel()
        if (id == _state.value.selectedCategoryId) return
        pendingSelection = viewModelScope.launch {
            delay(settleMs)
            selectCategory(id)
        }
    }

    /** Sélection immédiate (OK sur une catégorie). */
    fun selectCategory(id: String?) {
        pendingSelection?.cancel()
        if (id == _state.value.selectedCategoryId || catalog == null) return
        if (id != SEARCH && id != VirtualCategories.FAVORITES && id !in byCategory) return
        _state.update { it.copy(selectedCategoryId = id, channels = channelsFor(id)) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(settleMs)
            val c = catalog ?: return@launch
            val results = withContext(compute) { search(c, query) }
            searchResults = results
            if (_state.value.selectedCategoryId == SEARCH) _state.update { it.copy(channels = results) }
        }
    }

    fun toggleFavorite(channel: LiveChannel) {
        viewModelScope.launch { favoritesRepo.toggle(channel) }
    }

    /** Catégorie et position à transmettre au lecteur pour la chaîne [index] de la liste affichée. */
    fun playTarget(index: Int): Pair<String?, Int> {
        val s = _state.value
        lastPlayed[s.selectedCategoryId] = index
        if (s.selectedCategoryId != SEARCH) return s.selectedCategoryId to index
        // Recherche : on zappe ensuite dans toutes les chaînes.
        val channel = s.channels.getOrNull(index) ?: return null to 0
        val inAll = catalog?.channels?.indexOfFirst { it === channel } ?: 0
        return null to inAll.coerceAtLeast(0)
    }

    fun lastPlayedIndex(categoryId: String?): Int = lastPlayed[categoryId] ?: 0

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            accounts.logout()
            onDone()
        }
    }

    private suspend fun onFavoritesChanged(favs: Set<String>) {
        val c = catalog
        favoriteChannels = if (c == null) emptyList() else withContext(compute) { c.playlist(VirtualCategories.FAVORITES, favs) }
        _state.update {
            it.copy(
                favorites = favs,
                categories = if (c == null) it.categories else withFavorites(baseItems, favoriteChannels.size),
                channels = if (it.selectedCategoryId == VirtualCategories.FAVORITES) favoriteChannels else it.channels,
            )
        }
    }

    private fun channelsFor(id: String?): List<LiveChannel> = when (id) {
        SEARCH -> searchResults
        VirtualCategories.FAVORITES -> favoriteChannels
        else -> byCategory[id].orEmpty()
    }

    private fun withFavorites(items: List<CategoryItem>, count: Int): List<CategoryItem> =
        listOf(CategoryItem(VirtualCategories.FAVORITES, FAVORITES_NAME, count)) + items

    private fun search(c: LiveCatalog, query: String): List<LiveChannel> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val names = normalizedNames ?: Array(c.channels.size) { normalize(c.channels[it].name) }.also { normalizedNames = it }
        val words = q.split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<LiveChannel>()
        for (i in names.indices) {
            val n = names[i]
            if (words.all { n.contains(it) }) out += c.channels[i]
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }

    internal companion object {
        const val CATEGORY_SETTLE_MS = 250L
        const val MAX_RESULTS = 500
        private val MARKS = Regex("\\p{Mn}+")

        /** Minuscules, sans accents ni ponctuation : « TF1 HD » et « tf1-hd » se trouvent pareil. */
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(MARKS, "")
                .lowercase()
                .map { if (it.isLetterOrDigit()) it else ' ' }
                .joinToString("")
                .trim()
                .replace(Regex(" +"), " ")

        fun buildIndex(c: LiveCatalog): Pair<Map<String?, List<LiveChannel>>, List<CategoryItem>> {
            val grouped = c.channels.groupBy { it.categoryId }
            val index = HashMap<String?, List<LiveChannel>>(c.categories.size + 1)
            index[null] = c.channels
            val items = ArrayList<CategoryItem>(c.categories.size + 1)
            items += CategoryItem(null, ALL, c.channels.size)
            for (cat in c.categories) {
                val list = grouped[cat.id].orEmpty()
                index[cat.id] = list
                items += CategoryItem(cat.id, cat.name, list.size)
            }
            return index to items
        }
    }
}
