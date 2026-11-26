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
    override fun toString(): String =
        "LiveChannel(name=$name, number=$number, categoryId=$categoryId, streamId=$streamId, " +
            "url=${if (url == null) "null" else "***"})"
}

data class LiveCatalog(val categories: List<LiveCategory>, val channels: List<LiveChannel>) {
    /** Chaînes d'une catégorie, ou toutes si [categoryId] vaut `null`. */
    fun channelsOf(categoryId: String?): List<LiveChannel> =
        if (categoryId == null) channels else channels.filter { it.categoryId == categoryId }
}
