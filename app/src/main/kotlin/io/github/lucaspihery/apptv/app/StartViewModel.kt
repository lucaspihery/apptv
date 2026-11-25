package io.github.lucaspihery.apptv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Choisit l'écran de départ selon qu'un compte est enregistré ou non. */
@HiltViewModel
class StartViewModel @Inject constructor(accounts: AccountRepository) : ViewModel() {
    private val _startRoute = MutableStateFlow<Route?>(null)
    val startRoute: StateFlow<Route?> = _startRoute

    init {
        viewModelScope.launch {
            _startRoute.value = if (accounts.account.first() != null) Route.Home else Route.Login
        }
    }
}
