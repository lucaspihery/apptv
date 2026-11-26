package io.github.lucaspihery.apptv.core.data.live

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.account.SavedAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Chaînes favorites (« Mes chaînes ») du compte actif, gardées entre deux connexions. */
interface FavoritesRepository {
    /** Clés ([LiveChannel.key]) des favoris du compte actif. */
    val favorites: Flow<Set<String>>

    suspend fun toggle(channel: LiveChannel)
}

@Singleton
class DefaultFavoritesRepository @Inject constructor(
    @param:Named(STORE) private val store: DataStore<Preferences>,
    private val accounts: AccountRepository,
) : FavoritesRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val favorites: Flow<Set<String>> =
        combine(accounts.account, store.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }) { account, prefs ->
            account?.let { prefs[keyOf(it)] }.orEmpty()
        }.distinctUntilChanged()

    override suspend fun toggle(channel: LiveChannel) {
        val account = accounts.account.first() ?: return
        val key = keyOf(account)
        try {
            store.edit { p ->
                val current = p[key].orEmpty()
                p[key] = if (channel.key in current) current - channel.key else current + channel.key
            }
        } catch (_: IOException) {
            // Favori non enregistré : l'étoile ne change pas, l'UI reste cohérente.
        }
    }

    /** Une liste par compte : serveur + identifiant (jamais le mot de passe). */
    private fun keyOf(a: SavedAccount): Preferences.Key<Set<String>> =
        stringSetPreferencesKey("fav.${a.type}.${a.server}.${a.username.orEmpty()}")

    companion object {
        const val STORE = "favorites"
    }
}
