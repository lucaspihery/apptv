package io.github.lucaspihery.apptv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.live.LiveCatalog
import io.github.lucaspihery.apptv.core.data.live.LiveCategory
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.LiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Entrée de la colonne de gauche : `id == null` pour « Toutes les chaînes ». */
data class CategoryItem(val id: String?, val name: String, val count: Int)

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

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val live: LiveRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()
    private var catalog: LiveCatalog? = null

    init {
        load(refresh = false)
    }

    fun load(refresh: Boolean = true) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = live.catalog(refresh)) {
                is CoreResult.Err -> _state.update { it.copy(loading = false, error = r.error) }
                is CoreResult.Ok -> {
                    catalog = r.value
                    _state.update {
                        val selected = it.selectedCategoryId?.takeIf { id -> r.value.categories.any { c -> c.id == id } }
                        it.copy(
                            loading = false,
                            categories = categoryItems(r.value),
                            selectedCategoryId = selected,
                            channels = r.value.channelsOf(selected),
                        )
                    }
                }
            }
        }
    }

    fun selectCategory(id: String?) {
        val c = catalog ?: return
        if (id == _state.value.selectedCategoryId) return
        _state.update { it.copy(selectedCategoryId = id, channels = c.channelsOf(id)) }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            accounts.logout()
            onDone()
        }
    }

    private fun categoryItems(c: LiveCatalog): List<CategoryItem> {
        val counts = c.channels.groupingBy { it.categoryId }.eachCount()
        return buildList {
            add(CategoryItem(null, ALL, c.channels.size))
            c.categories.forEach { cat: LiveCategory -> add(CategoryItem(cat.id, cat.name, counts[cat.id] ?: 0)) }
        }
    }
}
