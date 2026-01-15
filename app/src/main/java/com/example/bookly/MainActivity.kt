package com.example.bookly

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bookly.ui.BookViewModel
import com.example.bookly.ui.EpubReaderScreen
import com.example.bookly.ui.HomeScreen
import com.example.bookly.ui.PdfReaderScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isSystemDark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (isSystemDark) darkColorScheme() else lightColorScheme()
            ) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !isSystemDark
                            isAppearanceLightNavigationBars = !isSystemDark
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    // 1. Inject ViewModel to access DB
    viewModel: BookViewModel = viewModel()
) {
    val navController = rememberNavController()

    // Observer the library to get current progress values
    val libraryBooks = viewModel.libraryBooks.collectAsState().value

    NavHost(navController = navController, startDestination = "home") {

        // HOME SCREEN
        composable("home") {
            HomeScreen(
                // IMPORTANT: You must update your HomeScreen callback to pass the ID too!
                // onBookClick = { bookId, path, format -> ... }
                // Assuming you have access to the book object in the loop:
                onBookClick = { bookId, path, format ->
                    val encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())

                    if (format.equals("pdf", ignoreCase = true)) {
                        // PDF route can stay simple for now (or update similarly if needed)
                        navController.navigate("pdf/$encodedPath")
                    } else {
                        // 2. Pass the ID in the route
                        navController.navigate("epub/$bookId/$encodedPath")
                    }
                }
            )
        }

        // PDF READER
        composable(
            route = "pdf/{bookUri}",
            arguments = listOf(navArgument("bookUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("bookUri") ?: ""
            val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
            PdfReaderScreen(uri = Uri.parse(decodedUri))
        }

        // EPUB READER ROUTE (Updated)
        composable(
            route = "epub/{bookId}/{bookUri}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.IntType },
                navArgument("bookUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0
            val encodedUri = backStackEntry.arguments?.getString("bookUri") ?: ""
            val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())

            // Get initial progress as Float
            val book = libraryBooks.find { it.id == bookId }
            val initialProgress = book?.progress ?: 0f // <--- Float default

            EpubReaderScreen(
                uri = Uri.parse(decodedUri),
                initialProgress = initialProgress,
                onSaveProgress = { newProgress ->
                    viewModel.updateBookProgress(bookId, newProgress)
                }
            )
        }
    }
}