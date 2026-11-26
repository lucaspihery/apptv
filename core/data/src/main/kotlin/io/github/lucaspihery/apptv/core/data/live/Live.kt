package io.github.lucaspihery.apptv.core.data.live

data class LiveCategory(val id: String, val name: String)

/**
 * Chaîne du direct. Aucun identifiant ici, sauf pour M3U où l'URL de la playlist est
 * la seule source du flux : [url] est alors masquée par `toString`.
 */
data class LiveChannel(
    val name: String,
    val number: Long?,
    val logo: String?,
    val categoryId: String?,
    val epgId: String?,
    /** Xtream : identifiant du flux. */
    val streamId: Long?,
    /** M3U : URL du flux, à ne jamais journaliser. */
    val url: String?,
) {
    /** Identifiant stable (favoris, cache du guide), sans secret. */
    val key: String
        get() = streamId?.let { "x$it" } ?: "m${url.hashCode()}:$name"

    override fun toString(): String =
        "LiveChannel(name=$name, number=$number, categoryId=$categoryId, streamId=$streamId, " +
            "url=${if (url == null) "null" else "***"})"
}

/** Programme du guide. Début et fin en secondes Unix. */
data class Program(val title: String, val description: String?, val start: Long, val end: Long) {
    fun isOnAir(nowSeconds: Long): Boolean = nowSeconds in start until end

    /** Avancement 0..1 du programme. */
    fun progress(nowSeconds: Long): Float =
        if (end <= start) 0f else ((nowSeconds - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

/** Catégories virtuelles de la colonne de gauche. */
object VirtualCategories {
    /** « Mes chaînes » : les favoris. */
    const val FAVORITES = "@fav"
}

data class LiveCatalog(val categories: List<LiveCategory>, val channels: List<LiveChannel>) {
    /** Chaînes d'une catégorie, ou toutes si [categoryId] vaut `null`. */
    fun channelsOf(categoryId: String?): List<LiveChannel> =
        if (categoryId == null) channels else channels.filter { it.categoryId == categoryId }

    /** Liste de zapping : une catégorie, toutes les chaînes, ou les favoris. */
    fun playlist(categoryId: String?, favorites: Set<String>): List<LiveChannel> =
        if (categoryId == VirtualCategories.FAVORITES) channels.filter { it.key in favorites } else channelsOf(categoryId)
}
