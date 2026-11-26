package io.github.lucaspihery.apptv.core.data.core

import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.XtreamLinkCredentials
import io.github.lucaspihery.apptv.core.ffi.CoreException
import io.github.lucaspihery.apptv.core.ffi.coreVersion
import io.github.lucaspihery.apptv.core.ffi.detectXtreamLink as ffiDetectXtreamLink
import io.github.lucaspihery.apptv.core.ffi.m3uLiveCatalog as ffiM3uLiveCatalog
import io.github.lucaspihery.apptv.core.ffi.m3uProbe
import io.github.lucaspihery.apptv.core.ffi.normalizeServer as ffiNormalizeServer
import io.github.lucaspihery.apptv.core.ffi.xtreamAuthenticate
import io.github.lucaspihery.apptv.core.ffi.xtreamLiveCatalog as ffiXtreamLiveCatalog
import io.github.lucaspihery.apptv.core.ffi.xtreamLiveUrl as ffiXtreamLiveUrl
import io.github.lucaspihery.apptv.core.data.live.LiveCatalog
import io.github.lucaspihery.apptv.core.data.live.LiveCategory
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'un appel au cœur Rust : valeur ou erreur typée. */
sealed interface CoreResult<out T> {
    data class Ok<T>(val value: T) : CoreResult<T>
    data class Err(val error: AccountError) : CoreResult<Nothing>
}

data class XtreamAccountInfo(
    val username: String,
    val expiresAt: Long?,
    val maxConnections: Long?,
    val allowedFormats: List<String>,
)

data class PlaylistInfo(val channelCount: Long, val groupCount: Long, val epgUrl: String?)

/**
 * Accès au cœur Rust. Interface pour pouvoir tester sans bibliothèque native.
 * Toutes les méthodes sont bloquantes : à appeler sur le dispatcher IO.
 */
interface IptvCore {
    fun version(): String
    fun normalizeServer(input: String): CoreResult<String>
    fun xtreamLogin(server: String, username: String, password: String): CoreResult<XtreamAccountInfo>
    fun probePlaylist(url: String): CoreResult<PlaylistInfo>
    fun detectXtreamLink(url: String): XtreamLinkCredentials?
    fun xtreamLiveCatalog(server: String, username: String, password: String): CoreResult<LiveCatalog>
    fun xtreamLiveUrl(server: String, username: String, password: String, streamId: Long, ext: String): CoreResult<String>
    fun m3uLiveCatalog(url: String): CoreResult<LiveCatalog>
}

@Singleton
class UniffiIptvCore @Inject constructor() : IptvCore {
    override fun version(): String = coreVersion()

    override fun normalizeServer(input: String): CoreResult<String> = call { ffiNormalizeServer(input) }

    override fun xtreamLogin(server: String, username: String, password: String) = call {
        val info = xtreamAuthenticate(server, username, password)
        XtreamAccountInfo(
            username = info.username,
            expiresAt = info.expiresAt,
            maxConnections = info.maxConnections,
            allowedFormats = info.allowedFormats,
        )
    }

    override fun probePlaylist(url: String) = call {
        val s = m3uProbe(url)
        PlaylistInfo(s.entryCount.toLong(), s.groupCount.toLong(), s.epgUrl)
    }

    override fun detectXtreamLink(url: String): XtreamLinkCredentials? =
        ffiDetectXtreamLink(url)?.let { XtreamLinkCredentials(it.server, it.username, it.password) }

    override fun xtreamLiveCatalog(server: String, username: String, password: String): CoreResult<LiveCatalog> = call {
        ffiXtreamLiveCatalog(server, username, password).toLive()
    }

    override fun xtreamLiveUrl(server: String, username: String, password: String, streamId: Long, ext: String): CoreResult<String> =
        call { ffiXtreamLiveUrl(server, username, password, streamId, ext) }

    override fun m3uLiveCatalog(url: String): CoreResult<LiveCatalog> = call { ffiM3uLiveCatalog(url).toLive() }

    private inline fun <T> call(block: () -> T): CoreResult<T> = try {
        CoreResult.Ok(block())
    } catch (e: CoreException) {
        CoreResult.Err(e.toAccountError())
    }
}

/** Conversion des erreurs Rust (déjà expurgées de tout secret). */
fun CoreException.toAccountError(): AccountError = when (this) {
    is CoreException.InvalidInput -> AccountError.InvalidInput(this.detail)
    is CoreException.Network -> AccountError.Network(this.detail)
    is CoreException.Timeout -> AccountError.Timeout
    is CoreException.Http -> AccountError.Http(this.status.toInt())
    is CoreException.AuthFailed -> AccountError.AuthFailed
    is CoreException.AccountInactive -> AccountError.AccountInactive(this.status)
    is CoreException.InvalidResponse -> AccountError.InvalidResponse(this.detail)
}

private fun io.github.lucaspihery.apptv.core.ffi.LiveCatalog.toLive() = LiveCatalog(
    categories = categories.map { LiveCategory(it.id, it.name) },
    channels = channels.map {
        LiveChannel(
            name = it.name,
            number = it.number,
            logo = it.logo,
            categoryId = it.categoryId,
            epgId = it.epgId,
            streamId = it.streamId,
            url = it.url,
        )
    },
)
