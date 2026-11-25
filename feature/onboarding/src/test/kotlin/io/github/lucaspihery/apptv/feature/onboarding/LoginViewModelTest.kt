package io.github.lucaspihery.apptv.feature.onboarding

import app.cash.turbine.test
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.LoginResult
import io.github.lucaspihery.apptv.core.data.account.XtreamLinkCredentials
import io.github.lucaspihery.apptv.core.testing.FakeAccountRepository
import io.github.lucaspihery.apptv.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeAccountRepository()
    private val vm = LoginViewModel(repo)

    private fun fillXtream() {
        vm.setServer("exemple.tv:8080")
        vm.setUsername("alice")
        vm.setPassword("secret")
    }

    @Test
    fun `le bouton n'est actif qu'avec tous les champs`() {
        assertFalse(vm.state.value.canSubmit)
        vm.setServer("exemple.tv")
        vm.setUsername("alice")
        assertFalse(vm.state.value.canSubmit)
        vm.setPassword("x")
        assertTrue(vm.state.value.canSubmit)
        vm.setMode(LoginMode.M3U)
        assertFalse(vm.state.value.canSubmit)
        vm.setM3uUrl("http://exemple.tv/liste.m3u")
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `connexion Xtream réussie`() = runTest {
        fillXtream()
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        vm.state.test {
            assertFalse(awaitItem().loading)
            vm.submit()
            assertTrue(awaitItem().loading)
            gate.complete(Unit)
            val done = awaitItem()
            assertFalse(done.loading)
            assertTrue(done.loggedIn)
            assertEquals("", done.password)
        }
        assertEquals(listOf("xtream exemple.tv:8080 alice"), repo.calls)
    }

    @Test
    fun `échec affiché puis effacé à la saisie`() = runTest {
        repo.nextResult = LoginResult.Failure(AccountError.AuthFailed)
        fillXtream()
        vm.submit()
        assertEquals(AccountError.AuthFailed, vm.state.value.error)
        assertFalse(vm.state.value.loggedIn)
        vm.setPassword("autre")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `pas de double envoi pendant le chargement`() = runTest {
        fillXtream()
        repo.gate = CompletableDeferred()
        vm.submit()
        vm.submit()
        assertEquals(1, repo.calls.size)
    }

    @Test
    fun `un lien get php propose la connexion Xtream`() {
        repo.xtreamLink = XtreamLinkCredentials("http://exemple.tv:8080", "bob", "pw")
        vm.setMode(LoginMode.M3U)
        vm.setM3uUrl("http://exemple.tv:8080/get.php?username=bob&password=pw")
        assertTrue(vm.state.value.xtreamLinkDetected)
        vm.useDetectedXtreamLink()
        val s = vm.state.value
        assertEquals(LoginMode.XTREAM, s.mode)
        assertEquals("http://exemple.tv:8080", s.server)
        assertEquals("bob", s.username)
        assertEquals("pw", s.password)
    }

    @Test
    fun `toString ne révèle pas le mot de passe`() {
        fillXtream()
        assertFalse(vm.state.value.toString().contains("secret"))
    }

    @Test
    fun `messages d'erreur sans secret`() {
        assertTrue(AccountError.AuthFailed.userMessage().isNotBlank())
        assertTrue(AccountError.Http(503).userMessage().contains("503"))
    }
}
