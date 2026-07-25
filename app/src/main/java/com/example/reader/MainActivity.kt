package com.example.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.reader.ui.reader.ReaderScreen
import com.example.reader.ui.shelf.ShelfScreen
import com.example.reader.ui.shelf.StatsScreen
import com.example.reader.ui.shelf.WifiTransferScreen
import com.example.reader.ui.theme.ReaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReaderTheme {
                ReaderNavigation()
            }
        }
    }
}

@Composable
fun ReaderNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "shelf"
    ) {
        composable("shelf") {
            ShelfScreen(
                onNavigateToReader = { bookPath ->
                    navController.navigate("reader/${java.net.URLEncoder.encode(bookPath, "UTF-8")}")
                },
                onNavigateToWifi = { navController.navigate("wifi") },
                onNavigateToStats = { navController.navigate("stats") }
            )
        }
        composable("wifi") {
            WifiTransferScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("stats") {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("reader/{bookPath}") { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("bookPath") ?: ""
            val bookPath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
            ReaderScreen(
                bookPath = bookPath,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
