package com.example.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.reader.ui.profile.ProfileScreen
import com.example.reader.ui.reader.ReaderScreen
import com.example.reader.ui.settings.SettingsScreen
import com.example.reader.ui.shelf.BookstorePlaceholder
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar on reader and other full-screen routes
    val showBottomBar = currentRoute !in listOf("reader/{bookPath}", "wifi", "stats", "settings")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "shelf",
                        onClick = {
                            navController.navigate("shelf") {
                                launchSingleTop = true
                                popUpTo("shelf") { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.LibraryBooks, contentDescription = null) },
                        label = { Text("书架") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "bookstore",
                        onClick = {
                            navController.navigate("bookstore") {
                                launchSingleTop = true
                                popUpTo("shelf") { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.Store, contentDescription = null) },
                        label = { Text("书城") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "profile",
                        onClick = {
                            navController.navigate("profile") {
                                launchSingleTop = true
                                popUpTo("shelf") { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                        label = { Text("个人") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "shelf",
            modifier = Modifier.padding(padding)
        ) {
            composable("shelf") {
                ShelfScreen(
                    onNavigateToReader = { bookPath ->
                        navController.navigate(
                            "reader/${java.net.URLEncoder.encode(bookPath, "UTF-8")}"
                        )
                    }
                )
            }
            composable("bookstore") {
                BookstorePlaceholder()
            }
            composable("profile") {
                ProfileScreen(
                    onNavigateToReader = { bookPath ->
                        navController.navigate(
                            "reader/${java.net.URLEncoder.encode(bookPath, "UTF-8")}"
                        )
                    },
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("wifi") {
                WifiTransferScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("stats") {
                StatsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
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
}
