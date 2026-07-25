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
                    navController.navigate("reader/$bookPath")
                }
            )
        }
        composable("reader/{bookPath}") { backStackEntry ->
            val bookPath = backStackEntry.arguments?.getString("bookPath") ?: ""
            ReaderScreen(
                bookPath = bookPath,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
