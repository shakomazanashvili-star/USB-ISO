package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.AuthManager
import com.example.data.IPanelRepository
import com.example.data.PanelRepository
import com.example.ui.PanelEditorScreen
import com.example.ui.PanelListScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val repository by lazy { PanelRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LaunchedEffect(Unit) {
                        AuthManager.trySilentSignIn(this@MainActivity)
                    }
                    PanelCraftApp(repository = repository)
                }
            }
        }
    }
}

@Composable
fun PanelCraftApp(repository: IPanelRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "panel_list") {
        composable("panel_list") {
            PanelListScreen(
                repository = repository,
                onSelectPanel = { panelId ->
                    navController.navigate("panel_editor/$panelId")
                }
            )
        }

        composable(
            route = "panel_editor/{panelId}",
            arguments = listOf(navArgument("panelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val panelId = backStackEntry.arguments?.getString("panelId") ?: ""
            PanelEditorScreen(
                panelId = panelId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
