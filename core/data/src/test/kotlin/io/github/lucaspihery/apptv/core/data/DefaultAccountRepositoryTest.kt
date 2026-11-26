package io.github.lucaspihery.apptv.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.AccountType
import io.github.lucaspihery.apptv.core.data.account.DefaultAccountRepository
import io.github.lucaspihery.apptv.core.data.account.LoginResult
import io.github.lucaspihery.apptv.core.data.account.XtreamLinkCredentials
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.core.IptvCore
import io.github.lucaspihery.apptv.core.data.core.PlaylistInfo
import io.github.lucaspihery.apptv.core.data.core.XtreamAccountInfo
import io.github.lucaspihery.apptv.core.data.crypto.AesGcmCipher
import io.github.lucaspihery.apptv.core.data.live.DefaultLiveRepository
import io.github.lucaspihery.apptv.core.data.live.LiveCatalog
import io.github.lucaspihery.apptv.core.data.live.LiveCategory
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.Program
import io.github.lucaspihery.apptv.core.data.live.DefaultEpgRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class DefaultAccountRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private class FakeCore : IptvCore {
        var login: CoreResult<XtreamAccountInfo> =
            CoreResult.Ok(XtreamAccountInfo("alice", 1_900_000_000, 2, listOf("ts")))
        var probe: CoreResult<PlaylistInfo> = CoreResult.Ok(PlaylistInfo(120, 8, null))
        val calls = mutableListOf<String>()

        override fun version() = "test"
        override fun normalizeServer(input: String): CoreResult<String> =
            if (input.isBlank()) CoreResult.Err(AccountError.InvalidInput("vide"))
            else CoreResult.Ok("http://" + input.removePrefix("http://").trimEnd('/'))
        override fun xtreamLogin(server: String, username: String, password: String) =
            login.also { calls += "login $server $username" }
        override fun probePlaylist(url: String) = probe.also { calls += "probe" }
        override fun detectXtreamLink(url: String): XtreamLinkCredentials? = null

        var live: CoreResult<LiveCatalog> = CoreResult.Ok(
            LiveCatalog(
                categories = listOf(LiveCategory("1", "TNT")),
                channels = listOf(LiveChannel("TF1", 1, null, "1", null, 101, null)),
            ),
        )
        override fun xtreamLiveCatalog(server: String, username: String, password: String) =
            live.also { calls += "live $server $username $password" }
        override fun xtreamLiveUrl(server: String, username: String, password: String, streamId: Long, ext: String) =
            CoreResult.Ok("$server/live/$username/$password/$streamId.$ext")
        override fun m3uLiveCatalog(url: String) = live.also { calls += "m3u live" }
        override fun xtreamShortEpg(server: String, username: String, password: String, streamId: Long, limit: Int) =
            CoreResult.Ok(listOf(Program("Journal", null, 0, Long.MAX_VALUE))).also { calls += "epg $streamId $limit" }
    }

    private fun TestScope.repo(core: FakeCore = FakeCore()): Pair<DefaultAccountRepository, java.io.File> {
        val file = java.io.File(tmp.root, "account.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DefaultAccountRepository(store, core, AesGcmCipher { key }, dispatcher) to file
    }

    @Test
    fun `connexion Xtream réussie enregistre le compte et chiffre le mot de passe`() = runTest {
        val core = FakeCore()
        val (repo, file) = repo(core)
        val result = repo.loginXtream("exemple.tv:8080/", " alice ", "MotDePasse!")
        assertTrue(result is LoginResult.Success)
        assertEquals(listOf("login http://exemple.tv:8080 alice"), core.calls)

        val saved = repo.account.first()!!
        assertEquals(AccountType.XTREAM, saved.type)
        assertEquals("http://exemple.tv:8080", saved.server)
        assertEquals("alice", saved.username)
        assertEquals(1_900_000_000L, saved.expiresAt)
        assertEquals("MotDePasse!", repo.secret())
        assertFalse("mot de passe en clair sur disque", file.readText(Charsets.ISO_8859_1).contains("MotDePasse"))
    }

    @Test
    fun `échec de connexion n'enregistre rien`() = runTest {
        val core = FakeCore().apply { login = CoreResult.Err(AccountError.AuthFailed) }
        val (repo, _) = repo(core)
        assertEquals(LoginResult.Failure(AccountError.AuthFailed), repo.loginXtream("h", "a", "b"))
        assertNull(repo.account.first())
    }

    @Test
    fun `adresse invalide sans appel réseau`() = runTest {
        val core = FakeCore()
        val (repo, _) = repo(core)
        val r = repo.loginXtream(" ", "a", "b")
        assertTrue(r is LoginResult.Failure && r.error is AccountError.InvalidInput)
        assertTrue(core.calls.isEmpty())
    }

    @Test
    fun `connexion M3U puis déconnexion`() = runTest {
        val (repo, file) = repo()
        val url = "http://exemple.tv/get.php?username=u&password=SecretM3u"
        assertTrue(repo.loginM3u(url) is LoginResult.Success)
        val saved = repo.account.first()!!
        assertEquals(AccountType.M3U, saved.type)
        assertEquals("exemple.tv", saved.server)
        assertEquals(120L, saved.channelCount)
        assertEquals(url, repo.secret())
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("SecretM3u"))

        repo.logout()
        assertNull(repo.account.first())
    }

    @Test
    fun `catalogue du direct chargé une fois puis gardé en mémoire`() = runTest {
        val core = FakeCore()
        val (repo, _) = repo(core)
        repo.loginXtream("exemple.tv", "alice", "secret")
        val live = DefaultLiveRepository(repo, core, StandardTestDispatcher(testScheduler))
        val first = live.catalog()
        assertTrue(first is CoreResult.Ok)
        live.catalog()
        assertEquals(1, core.calls.count { it.startsWith("live") })
        assertTrue(core.calls.contains("live http://exemple.tv alice secret"))
        val channel = (first as CoreResult.Ok).value.channels.single()
        assertEquals("http://exemple.tv/live/alice/secret/101.ts", live.streamUrl(channel))
        assertFalse(channel.toString().contains("secret"))
    }

    @Test
    fun `chaîne M3U lue depuis son URL masquée dans toString`() = runTest {
        val c = LiveChannel("F2", null, null, null, null, null, "http://h/u/motdepasse/2.ts")
        assertFalse(c.toString().contains("motdepasse"))
        val catalog = LiveCatalog(listOf(LiveCategory("a", "A")), listOf(c, c.copy(categoryId = "a")))
        assertEquals(1, catalog.channelsOf("a").size)
        assertEquals(2, catalog.channelsOf(null).size)
    }

    @Test
    fun `guide demandé une fois puis servi depuis le cache`() = runTest {
        val core = FakeCore()
        val (repo, _) = repo(core)
        repo.loginXtream("exemple.tv", "alice", "secret")
        val epg = DefaultEpgRepository(repo, core, StandardTestDispatcher(testScheduler))
        val tf1 = LiveChannel("TF1", 1, null, "1", null, 101, null)
        assertEquals(null, epg.cached(tf1))
        assertEquals("Journal", epg.programs(tf1).single().title)
        assertEquals("Journal", epg.programs(tf1).single().title)
        assertEquals(1, core.calls.count { it.startsWith("epg 101") })
        // Chaîne M3U : pas de guide, pas d'appel.
        assertTrue(epg.programs(LiveChannel("F2", null, null, null, null, null, "http://h/2.ts")).isEmpty())
    }

    @Test
    fun `liste de zapping des favoris`() {
        val a = LiveChannel("A", null, null, "c", null, 1, null)
        val b = LiveChannel("B", null, null, "c", null, 2, null)
        val catalog = LiveCatalog(listOf(LiveCategory("c", "C")), listOf(a, b))
        assertEquals(listOf(b), catalog.playlist(io.github.lucaspihery.apptv.core.data.live.VirtualCategories.FAVORITES, setOf("x2")))
        assertEquals(2, catalog.playlist("c", emptySet()).size)
    }
}
