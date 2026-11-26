package io.github.lucaspihery.apptv.app

import io.github.lucaspihery.apptv.core.data.account.AccountType
import io.github.lucaspihery.apptv.core.data.account.SavedAccount
import io.github.lucaspihery.apptv.core.testing.FakeAccountRepository
import io.github.lucaspihery.apptv.core.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StartViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `sans compte on démarre sur la connexion`() {
        val vm = StartViewModel(FakeAccountRepository())
        assertEquals(Route.Login, vm.startRoute.value)
    }

    @Test
    fun `avec un compte on démarre sur l'accueil`() {
        val repo = FakeAccountRepository().apply {
            setAccount(SavedAccount(AccountType.XTREAM, "http://h", "alice", null, null, null))
        }
        assertEquals(Route.Home, StartViewModel(repo).startRoute.value)
    }
}
