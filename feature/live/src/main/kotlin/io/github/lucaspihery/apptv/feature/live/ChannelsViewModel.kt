package io.github.lucaspihery.apptv.feature.live

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.live.LiveCatalog
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.LiveRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Entrée de la colonne de gauche : `id == null` pour « Toutes les chaînes ». */
@Immutable
data class CategoryItem(val id: String?, val name: String, val count: Int)

@Immutable
data class ChannelsUiState(
    val loading: Boolean = true,
    val error: AccountError? = null,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategoryId: String? = null,
    val channels: List<LiveChannel> = emptyList(),
) {
    val selectedCategoryName: String
        get() = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: ALL
}

internal const val ALL = "Toutes les chaînes"

/**
 * Fluidité sur une TV modeste avec des dizaines de milliers de chaînes :
 * - l'index « catégorie → chaînes » est calculé une seule fois, hors du thread UI ;
 * - changer de catégorie est une simple lecture dans cet index ;
 * - parcourir les catégories au D-pad ne remplace la liste qu'une fois le focus posé
 *   ([CATEGORY_SETTLE_MS]), pas à chaque catégorie traversée.
 */
@HiltViewModel
class ChannelsViewModel internal constructor(
    private val live: LiveRepository,
    private val accounts: AccountRepository,
    private val compute: CoroutineDispatcher,
    private val settleMs: Long,
) : ViewModel() {

    @Inject constructor(live: LiveRepository, accounts: AccountRepository) :
        this(live, accounts, Dispatchers.Default, CATEGORY_SETTLE_MS)

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()
    private var byCategory: Map<String?, List<LiveChannel>> = emptyMap()
    private var pendingSelection: Job? = null

    /** Dernière chaîne ouverte par catégorie, pour y revenir au retour du lecteur. */
    private val lastPlayed = HashMap<String?, Int>()

    init {
        load(refresh = false)
    }

    fun load(refresh: Boolean = true) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = live.catalog(refresh)) {
                is CoreResult.Err -> _state.update { it.copy(loading = false, error = r.error) }
                is CoreResult.Ok -> {
                    val (index, items) = withContext(compute) { buildIndex(r.value) }
                    byCategory = index
                    _state.update {
                        val selected = it.selectedCategoryId?.takeIf { id -> id in index }
                        it.copy(
                            loading = false,
                            categories = items,
                            selectedCategoryId = selected,
                            channels = index[selected].orEmpty(),
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
        if (id == _state.value.selectedCategoryId || id !in byCategory) return
        _state.update { it.copy(selectedCategoryId = id, channels = byCategory[id].orEmpty()) }
    }

    fun onPlayed(categoryId: String?, index: Int) {
        lastPlayed[categoryId] = index
    }

    fun lastPlayedIndex(categoryId: String?): Int = lastPlayed[categoryId] ?: 0

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            accounts.logout()
            onDone()
        }
    }

    internal companion object {
        const val CATEGORY_SETTLE_MS = 250L

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
