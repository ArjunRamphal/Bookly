package com.example.bookly.ui

import android.app.Activity
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient // Added Import
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.example.bookly.utils.EpubUtils
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- PDF READER ---
@Composable
fun PdfReaderScreen(uri: Uri) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> PDFView(context, null) },
        update = { pdfView ->
            pdfView.fromUri(uri)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .spacing(10)
                .load()
        }
    )
}

// --- EPUB READER ---
@Composable
fun EpubReaderScreen(
    uri: Uri,
    initialProgress: Float = 0f, // Accept saved progress
    onSaveProgress: (Float) -> Unit = {} // Callback to save on exit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // --- STATE ---
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var fontSize by remember { mutableIntStateOf(100) }
    var isDarkMode by remember { mutableStateOf(false) }

    // Track current progress
    var progressPercentage by remember { mutableFloatStateOf(initialProgress) }

    var currentProgress by remember { mutableFloatStateOf(initialProgress) }

    // --- AUTO-SAVE ON EXIT ---
    DisposableEffect(Unit) {
        onDispose {
            onSaveProgress(currentProgress)
        }
    }

    // --- SYSTEM BAR ICON CONTROL ---
    LaunchedEffect(isDarkMode) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)

        if (isDarkMode) {
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        } else {
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    // Load content
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            htmlContent = EpubUtils.parseEpub(context, uri)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                ReaderControls(
                    fontSize = fontSize,
                    isDarkMode = isDarkMode,
                    onFontChange = { newSize -> fontSize = newSize.coerceIn(50, 200) },
                    onThemeToggle = { isDarkMode = !isDarkMode }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color(0xFF121212) else Color.White)
                .padding(padding)
        ) {
            if (htmlContent == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false

                            // --- RESTORE PROGRESS ---
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (initialProgress > 0f) {
                                        val jsScroll = "window.scrollTo(0, document.body.scrollHeight * ($initialProgress / 100));"
                                        evaluateJavascript(jsScroll, null)
                                    }
                                }
                            }

                            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapUp(e: MotionEvent): Boolean {
                                    showControls = !showControls
                                    return super.onSingleTapUp(e)
                                }
                            })

                            setOnTouchListener { _, event ->
                                gestureDetector.onTouchEvent(event)
                                false
                            }

                            // Scroll Listener
                            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                                val totalHeight = (contentHeight * scale)
                                val viewportHeight = height.toFloat()
                                val scrollableHeight = totalHeight - viewportHeight

                                if (scrollableHeight > 0) {
                                    // Calculate exact percentage
                                    val rawProgress = (scrollY.toFloat() / scrollableHeight) * 100f
                                    currentProgress = rawProgress.coerceIn(0f, 100f)
                                }
                            }
                        }
                    },
                    update = { webView ->
                        webView.settings.textZoom = fontSize

                        if (webView.url == null) {
                            webView.loadDataWithBaseURL(null, htmlContent!!, "text/html", "UTF-8", null)
                        }

                        val js = if (isDarkMode) {
                            """
                            document.body.style.backgroundColor = '#121212';
                            document.body.style.color = '#e0e0e0';
                            """
                        } else {
                            """
                            document.body.style.backgroundColor = '#ffffff';
                            document.body.style.color = '#000000';
                            """
                        }
                        webView.evaluateJavascript(js, null)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                ReadingStatusBar(
                    progress = currentProgress.toInt(),
                    isDarkMode = isDarkMode,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                )
            }
        }
    }
}

// --- STATUS BAR ---
@Composable
fun ReadingStatusBar(
    progress: Int,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(24.dp),
        color = if (isDarkMode) Color.Black.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color.LightGray else Color.DarkGray
                )
            )
        }
    }
}

// --- CONTROLS UI ---
@Composable
fun ReaderControls(
    fontSize: Int,
    isDarkMode: Boolean,
    onFontChange: (Int) -> Unit,
    onThemeToggle: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme"
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { onFontChange(fontSize - 10) }) {
                    Icon(Icons.Default.TextDecrease, contentDescription = "Decrease Font")
                }
                Text(
                    text = "${fontSize}%",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(onClick = { onFontChange(fontSize + 10) }) {
                    Icon(Icons.Default.TextIncrease, contentDescription = "Increase Font")
                }
            }
        }
    }
}