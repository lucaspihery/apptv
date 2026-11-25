package io.github.lucaspihery.apptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassBackground
import io.github.lucaspihery.apptv.feature.onboarding.LoginRoute
import kotlinx.serialization.Serializable

/** Destinations typées (Navigation Compose + kotlinx.serialization). */
sealed interface Route {
    @Serializable data object Login : Route
    @Serializable data object Home : Route
}

@Composable
fun ApptvNavHost(viewModel: StartViewModel = hiltViewModel()) {
    val start by viewModel.startRoute.collectAsStateWithLifecycle()
    val route = start
    if (route == null) {
        // Lecture du compte enregistré (quelques ms) : fond seul, sans clignotement.
        GlassBackground { Box(Modifier.fillMaxSize()) }
        return
    }
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = route) {
        composable<Route.Login> {
            LoginRoute(onLoggedIn = {
                nav.navigate(Route.Home) { popUpTo<Route.Login> { inclusive = true } }
            })
        }
        composable<Route.Home> {
            HomeScreen(onLoggedOut = {
                nav.navigate(Route.Login) { popUpTo<Route.Home> { inclusive = true } }
            })
        }
    }
}
