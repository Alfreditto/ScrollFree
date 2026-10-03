package es.tortugaselectronicas.scrollfree.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import es.tortugaselectronicas.scrollfree.ui.history.HistoryScreen
import es.tortugaselectronicas.scrollfree.ui.home.DownloadPhase
import es.tortugaselectronicas.scrollfree.ui.home.DownloadViewModel
import es.tortugaselectronicas.scrollfree.ui.home.HomeScreen
import es.tortugaselectronicas.scrollfree.ui.player.PlayerScreen
import es.tortugaselectronicas.scrollfree.ui.player.PlayerViewModel
import es.tortugaselectronicas.scrollfree.ui.player.SEEK_STEP_MS
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val PLAYER = "player"
    const val PLAYER_ARG_PATH = "path"
    const val PLAYER_ROUTE = "$PLAYER?$PLAYER_ARG_PATH={$PLAYER_ARG_PATH}"
}

@Composable
fun ScrollFreeApp(
    incomingUrl: SharedFlow<String>
) {
    val navController = rememberNavController()
    val downloadViewModel: DownloadViewModel = viewModel()

    // Forward links coming from ACTION_SEND / ACTION_VIEW into the download field.
    LaunchedEffect(Unit) {
        incomingUrl.collect { url ->
            downloadViewModel.onIncomingUrl(url)
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val downloadState by downloadViewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = downloadState,
                onUrlChange = downloadViewModel::onUrlChange,
                onDownloadClick = downloadViewModel::onDownloadClick,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onUpdateExtractor = downloadViewModel::onUpdateExtractor,
                onSnackbarConsumed = downloadViewModel::onSnackbarConsumed
            )

            // Navigate to the player as soon as a download completes.
            LaunchedEffect(downloadState.phase) {
                val phase = downloadState.phase
                if (phase is DownloadPhase.Ready) {
                    val encoded = android.net.Uri.encode(phase.file.absolutePath)
                    navController.navigate("${Routes.PLAYER}?${Routes.PLAYER_ARG_PATH}=$encoded")
                    downloadViewModel.onPlayerConsumed()
                }
            }
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                entries = downloadState.history,
                onBack = { navController.popBackStack() },
                onClearHistory = downloadViewModel::onClearHistory
            )
        }

        composable(
            route = Routes.PLAYER_ROUTE,
            arguments = listOf(
                navArgument(Routes.PLAYER_ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString(Routes.PLAYER_ARG_PATH)
            val playerViewModel: PlayerViewModel = viewModel()
            val playerState by playerViewModel.uiState.collectAsState()

            LaunchedEffect(path) {
                if (!path.isNullOrBlank()) {
                    val file = File(android.net.Uri.decode(path))
                    if (file.exists()) playerViewModel.setMedia(file)
                }
            }

            PlayerScreen(
                player = playerViewModel.player,
                state = playerState,
                onBack = { navController.popBackStack() },
                onSeekBackward10 = { playerViewModel.seekBy(-SEEK_STEP_MS) },
                onSeekForward10 = { playerViewModel.seekBy(SEEK_STEP_MS) },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSeekToFraction = playerViewModel::seekToFraction,
                onSpeedChange = playerViewModel::setSpeed
            )
        }
    }
}
