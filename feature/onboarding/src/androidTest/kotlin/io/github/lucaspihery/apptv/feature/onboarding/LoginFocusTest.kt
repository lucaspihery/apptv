package io.github.lucaspihery.apptv.feature.onboarding

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaspihery.apptv.core.designsystem.theme.ApptvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation au D-pad sur l'écran de connexion (émulateur ou TV). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LoginFocusTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: LoginUiState = LoginUiState()) = rule.setContent {
        ApptvTheme {
            LoginScreen(state, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun le_premier_champ_a_le_focus_au_demarrage() {
        show()
        rule.onNodeWithTag(LoginTags.SERVER).assertIsFocused()
    }

    @Test
    fun la_fleche_bas_parcourt_les_champs_sans_ouvrir_le_clavier() {
        show()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(LoginTags.USERNAME).assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(LoginTags.PASSWORD).assertIsFocused()
    }

    @Test
    fun la_fleche_haut_mene_aux_onglets() {
        show()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag(LoginTags.MODE_XTREAM).assertIsFocused()
    }
}
