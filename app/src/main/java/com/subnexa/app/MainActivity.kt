package com.subnexa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.subnexa.app.ui.editor.EditorScreen
import com.subnexa.app.ui.editor.EditorViewModel
import com.subnexa.app.ui.home.HomeScreen
import com.subnexa.app.ui.home.HomeViewModel
import com.subnexa.app.ui.settings.SettingsScreen
import com.subnexa.app.ui.settings.SettingsViewModel
import com.subnexa.app.ui.theme.Background
import com.subnexa.app.ui.theme.SubNexaTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SubNexaTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Background
        ) {
          SubNexaAppNavigation()
        }
      }
    }
  }
}

@Composable
fun SubNexaAppNavigation() {
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = "home",
    // Short, light fades instead of the slow default crossfade (700 ms).
    enterTransition = { fadeIn(animationSpec = tween(180)) },
    exitTransition = { fadeOut(animationSpec = tween(120)) },
    popEnterTransition = { fadeIn(animationSpec = tween(180)) },
    popExitTransition = { fadeOut(animationSpec = tween(120)) }
  ) {
    composable("home") {
      val homeViewModel: HomeViewModel = viewModel()
      HomeScreen(
        viewModel = homeViewModel,
        onNavigateToEditor = { projectId ->
          navController.navigate("editor/$projectId")
        },
        onNavigateToSettings = {
          navController.navigate("settings")
        }
      )
    }

    composable(
      route = "editor/{projectId}",
      arguments = listOf(
        navArgument("projectId") { type = NavType.StringType }
      )
    ) { backStackEntry ->
      val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
      val editorViewModel: EditorViewModel = viewModel()
      EditorScreen(
        projectId = projectId,
        viewModel = editorViewModel,
        onNavigateBack = {
          // Stop playback first so no video/audio keeps running during the exit animation.
          editorViewModel.pause()
          navController.popBackStack()
        }
      )
    }

    composable("settings") {
      val settingsViewModel: SettingsViewModel = viewModel()
      SettingsScreen(
        viewModel = settingsViewModel,
        onNavigateBack = {
          navController.popBackStack()
        }
      )
    }
  }
}
