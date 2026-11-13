package es.tortugaselectronicas.scrollfree.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import es.tortugaselectronicas.scrollfree.data.MediaKind
import es.tortugaselectronicas.scrollfree.ui.about.AboutScreen
import es.tortugaselectronicas.scrollfree.ui.history.HistoryScreen
import es.tortugaselectronicas.scrollfree.ui.home.DownloadPhase
import es.tortugaselectronicas.scrollfree.ui.home.DownloadViewModel
import es.tortugaselectronicas.scrollfree.ui.home.HomeScreen
import es.tortugaselectronicas.scrollfree.ui.photos.PhotoCarouselScreen
import es.tortugaselectronicas.scrollfree.ui.photos.PhotoCarouselViewModel
import es.tortugaselectronicas.scrollfree.ui.player.PlayerScreen
import es.tortugaselectronicas.scrollfree.ui.player.PlayerViewModel
import es.tortugaselectronicas.scrollfree.ui.player.SEEK_STEP_MS
import es.tortugaselectronicas.scrollfree.ui.tweet.TweetScreen
import es.tortugaselectronicas.scrollfree.update.UpdateViewModel
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ABOUT = "about"
    const val PLAYER = "player"
    const val PHOTOS = "photos"
    const val TWEET = "tweet"
    const val ARG_PATH = "path"
    const val PLAYER_ROUTE = "$PLAYER?$ARG_PATH={$ARG_PATH}"
    const val PHOTOS_ROUTE = "$PHOTOS?$ARG_PATH={$ARG_PATH}"
}

@Composable
fun ScrollFreeApp(
    incomingUrl: SharedFlow<String>
) {
    val navController = rememberNavController()
    val downloadViewModel: DownloadViewModel = viewModel()

    // Checks GitHub Releases once on start; only drives an "update available" banner.
    val updateViewModel: UpdateViewModel = viewModel()
    val updateAvailable by updateViewModel.available.collectAsState()
    LaunchedEffect(Unit) { updateViewModel.checkOnce() }

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

    // Holds the tweet being displayed (text + files) between Home and TweetScreen.
    var downloadedTweet by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<MediaKind.Tweet?>(null)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = downloadState,
                onUrlChange = downloadViewModel::onUrlChange,
                onDownloadClick = downloadViewModel::onDownloadClick,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onUpdateExtractor = downloadViewModel::onUpdateExtractor,
                onSnackbarConsumed = downloadViewModel::onSnackbarConsumed,
                updateAvailable = updateAvailable,
                onDismissUpdate = updateViewModel::dismiss,
                onOpenAbout = {
                    updateViewModel.resetManualCheck()
                    navController.navigate(Routes.ABOUT)
                }
            )

            // Navigate to the right screen as soon as a download completes.
            LaunchedEffect(downloadState.phase) {
                val phase = downloadState.phase
                if (phase is DownloadPhase.Ready) {
                    when (val kind = phase.kind) {
                        is MediaKind.Video -> {
                            val encoded = android.net.Uri.encode(kind.file.absolutePath)
                            navController.navigate("${Routes.PLAYER}?${Routes.ARG_PATH}=$encoded")
                        }

                        is MediaKind.PhotoCarousel -> {
                            // Pass the downloaded file paths (images + audio) as one CSV arg.
                            val files = buildList {
                                addAll(kind.images)
                                kind.audio?.let { add(it) }
                            }.joinToString("|") { it.absolutePath }
                            val encoded = android.net.Uri.encode(files)
                            navController.navigate("${Routes.PHOTOS}?${Routes.ARG_PATH}=$encoded")
                        }

                        is MediaKind.Tweet -> {
                            // Tweets carry text + many files; keep them in the VM and
                            // navigate with just the route.
                            downloadedTweet = kind
                            navController.navigate(Routes.TWEET)
                        }
                    }
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

        composable(Routes.ABOUT) {
            val manualCheck by updateViewModel.manualCheck.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current
            AboutScreen(
                manualCheck = manualCheck,
                onBack = { navController.popBackStack() },
                onCheckUpdates = updateViewModel::checkNow,
                onOpenRelease = { url ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                        )
                    }
                }
            )
        }

        composable(
            route = Routes.PLAYER_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString(Routes.ARG_PATH)
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

        composable(
            route = Routes.PHOTOS_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rawPaths = backStackEntry.arguments?.getString(Routes.ARG_PATH)
            val carouselViewModel: PhotoCarouselViewModel = viewModel()
            val carouselState by carouselViewModel.uiState.collectAsState()

            LaunchedEffect(rawPaths) {
                if (!rawPaths.isNullOrBlank()) {
                    val decoded = android.net.Uri.decode(rawPaths)
                    val files = decoded.split("|")
                        .filter { it.isNotBlank() }
                        .map { File(it) }
                        .filter { it.exists() }
                    // Separate images from the single audio track (m4a/mp3/... by extension).
                    val audio = files.firstOrNull { isAudioFile(it) }
                    val images = files.filter { !isAudioFile(it) }
                    if (images.isNotEmpty()) carouselViewModel.setContent(images, audio)
                }
            }

            PhotoCarouselScreen(
                state = carouselState,
                onBack = { navController.popBackStack() },
                onTogglePlayPause = carouselViewModel::togglePlayPause,
                onPageSelected = carouselViewModel::goTo
            )
        }

        composable(Routes.TWEET) {
            val tweet = downloadedTweet
            if (tweet == null) {
                // Nothing to show (e.g. process death) – bounce back home.
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                TweetScreen(
                    tweet = tweet,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { file ->
                        val encoded = android.net.Uri.encode(file.absolutePath)
                        navController.navigate("${Routes.PLAYER}?${Routes.ARG_PATH}=$encoded")
                    }
                )
            }
        }
    }
}

private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "opus", "ogg", "wav", "webm")

private fun isAudioFile(file: File): Boolean =
    file.extension.lowercase() in AUDIO_EXTENSIONS
