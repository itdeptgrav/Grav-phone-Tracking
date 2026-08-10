package com.personal.callrecorder.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.callrecorder.ui.callDetails.CallDetailScreen
import com.personal.callrecorder.ui.diagnostics.AudioTestScreen
import com.personal.callrecorder.ui.diagnostics.DiagnosticsScreen
import com.personal.callrecorder.ui.home.HomeScreen
import com.personal.callrecorder.ui.onboarding.FirstRunScreen
import com.personal.callrecorder.ui.onboarding.LockScreen
import com.personal.callrecorder.ui.search.SearchScreen
import com.personal.callrecorder.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val AUDIO_TEST = "audio_test"
    const val CALL_DETAIL = "call/{callId}"
    fun callDetail(id: Long) = "call/$id"
}

/** Top of the UI tree: legal gate → biometric gate → main navigation. */
@Composable
fun AppRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val gate by appViewModel.gate.collectAsStateWithLifecycle()
    var unlocked by rememberSaveable { mutableStateOf(false) }

    when {
        !gate.loaded -> LoadingScreen()
        !gate.legalAccepted -> FirstRunScreen(onAccept = appViewModel::acceptLegal)
        gate.biometricLock && !unlocked -> LockScreen(onUnlocked = { unlocked = true })
        else -> MainNavHost()
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenCall = { id -> navController.navigate(Routes.callDetail(id)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenCall = { id -> navController.navigate(Routes.callDetail(id)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                onBack = { navController.popBackStack() },
                onOpenAudioTest = { navController.navigate(Routes.AUDIO_TEST) }
            )
        }
        composable(Routes.AUDIO_TEST) {
            AudioTestScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CALL_DETAIL,
            arguments = listOf(navArgument("callId") { type = NavType.LongType })
        ) {
            CallDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
