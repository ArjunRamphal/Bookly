package com.example.bookly.ui

import android.app.Activity
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bookly.utils.BookUtils
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- PDF READER (Unchanged) ---
@Composable
fun PdfReaderScreen(
    uri: Uri,
    initialProgress: Float = 0f,
    onSaveProgress: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var currentProgress by remember { mutableFloatStateOf(initialProgress) }
    var targetPage by remember { mutableStateOf<Int?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    val count = renderer.pageCount
                    if (count > 0) {
                        val maxIndex = (count - 1).coerceAtLeast(1)
                        val page = ((initialProgress / 100f) * maxIndex).toInt()
                        targetPage = page.coerceIn(0, count - 1)
                    } else {
                        targetPage = 0
                    }
                    renderer.close()
                }
            } catch (e: Exception) {
                targetPage = 0
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onSaveProgress(currentProgress) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (targetPage != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PDFView(ctx, null) },
                update = { pdfView ->
                    if (!isLoaded) {
                        pdfView.fromUri(uri)
                            .swipeHorizontal(false)
                            .enableDoubletap(true)
                            .spacing(10)
                            .defaultPage(targetPage!!)
                            .onPageChange { page, pageCount ->
                                if (pageCount > 1) {
                                    val maxIndex = (pageCount - 1).toFloat()
                                    val percentage = (page.toFloat() / maxIndex) * 100f
                                    currentProgress = percentage.coerceIn(0f, 100f)
                                } else {
                                    currentProgress = 100f
                                }
                            }
                            .load()
                        isLoaded = true
                    }
                }
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        ReadingStatusBar(
            progress = currentProgress.toInt(),
            isDarkMode = false,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        )
    }
}

// --- EPUB / TXT / RTF READER ---

private enum class ScrollTarget {
    TOP, BOTTOM, SAVED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    uri: Uri,
    initialProgress: Float = 0f, // 0-100%
    onSaveProgress: (Float) -> Unit = {},
    viewModel: BookViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val systemInDarkTheme = isSystemInDarkTheme()

    // --- STATE ---
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var fontSize by remember { mutableIntStateOf(100) }
    var isDarkMode by remember { mutableStateOf(systemInDarkTheme) }

    // Font State
    val availableFonts by viewModel.availableFonts.collectAsState()
    val selectedFont by viewModel.selectedFont.collectAsState()
    var showFontSheet by remember { mutableStateOf(false) }

    // Navigation State
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var totalChapters by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    // Restoration State
    var savedScrollFraction by remember { mutableFloatStateOf(0f) }

    // Scroll Target State
    var scrollTarget by remember { mutableStateOf(ScrollTarget.SAVED) }

    // Live Progress (0-100%)
    var globalProgressPercent by remember { mutableFloatStateOf(initialProgress) }

    fun getThemeJs(dark: Boolean): String {
        return if (dark) "document.body.style.backgroundColor = '#121212'; document.body.style.color = '#e0e0e0';"
        else "document.body.style.backgroundColor = '#ffffff'; document.body.style.color = '#000000';"
    }

    // Prepare HTML with CSS injection for Custom Fonts
    val finalHtml = remember(htmlContent, selectedFont) {
        val content = htmlContent ?: ""
        if (selectedFont == null) {
            content
        } else {
            """
            <html>
            <head>
            <style>
                @font-face {
                    font-family: 'CustomFont';
                    src: url('fonts/$selectedFont');
                }
                body, p, div, span, h1, h2, h3, h4, h5, h6 {
                    font-family: 'CustomFont', sans-serif !important;
                }
            </style>
            </head>
            <body>
            $content
            </body>
            </html>
            """
        }
    }

    // Save global percentage on exit
    DisposableEffect(Unit) {
        onDispose { onSaveProgress(globalProgressPercent) }
    }

    LaunchedEffect(isDarkMode) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode
    }

    // --- INITIAL LOAD ---
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val uriString = uri.toString()
            val isTxt = uriString.endsWith("txt", ignoreCase = true)
            val isRtf = uriString.endsWith("rtf", ignoreCase = true)

            if (isTxt) {
                totalChapters = 1
                htmlContent = BookUtils.parseTxt(context, uri)
                savedScrollFraction = initialProgress / 100f
                isLoading = false
            } else if (isRtf) {
                totalChapters = 1
                htmlContent = BookUtils.parseRtf(context, uri)
                savedScrollFraction = initialProgress / 100f
                isLoading = false
            } else {
                val chapters = BookUtils.getChapters(context, uri)
                totalChapters = chapters.size

                if (totalChapters > 0) {
                    val rawIndex = (initialProgress / 100f) * totalChapters
                    currentChapterIndex = rawIndex.toInt().coerceIn(0, totalChapters - 1)
                    savedScrollFraction = rawIndex - currentChapterIndex
                } else {
                    currentChapterIndex = 0
                }

                htmlContent = BookUtils.loadChapter(context, uri, currentChapterIndex)
                isLoading = false
            }
        }
    }

    // --- RELOAD CHAPTER ---
    LaunchedEffect(currentChapterIndex) {
        if (totalChapters > 1 && !isLoading) {
            isLoading = true
            withContext(Dispatchers.IO) {
                htmlContent = BookUtils.loadChapter(context, uri, currentChapterIndex)
                isLoading = false
            }
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
                Column {
                    if (totalChapters > 1) {
                        ChapterNavigation(
                            currentIndex = currentChapterIndex,
                            total = totalChapters,
                            onPrev = {
                                if (currentChapterIndex > 0) {
                                    scrollTarget = ScrollTarget.BOTTOM
                                    currentChapterIndex--
                                    if (totalChapters > 0) {
                                        globalProgressPercent = ((currentChapterIndex + 1f) / totalChapters) * 100f
                                    }
                                }
                            },
                            onNext = {
                                if (currentChapterIndex < totalChapters - 1) {
                                    scrollTarget = ScrollTarget.TOP
                                    currentChapterIndex++
                                    if (totalChapters > 0) {
                                        globalProgressPercent = (currentChapterIndex.toFloat() / totalChapters) * 100f
                                    }
                                }
                            },
                            onJumpTo = { newIndex ->
                                if (newIndex in 0 until totalChapters) {
                                    scrollTarget = ScrollTarget.TOP
                                    currentChapterIndex = newIndex
                                    if (totalChapters > 0) {
                                        globalProgressPercent = (newIndex.toFloat() / totalChapters) * 100f
                                    }
                                }
                            }
                        )
                        Divider()
                    }
                    ReaderControls(
                        fontSize = fontSize,
                        isDarkMode = isDarkMode,
                        onFontChange = { newSize -> fontSize = newSize.coerceIn(50, 200) },
                        onThemeToggle = { isDarkMode = !isDarkMode },
                        onOpenFontSheet = { showFontSheet = true }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color(0xFF121212) else Color.White)
                .padding(padding)
        ) {
            if (isLoading || htmlContent == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            // CRITICAL: Allow WebView to access local file storage for fonts
                            settings.allowFileAccess = true

                            var dragStartY = 0f
                            val OVERSCROLL_THRESHOLD = 150f

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(getThemeJs(isDarkMode), null)

                                    when (scrollTarget) {
                                        ScrollTarget.TOP -> {
                                            view?.scrollTo(0, 0)
                                        }
                                        ScrollTarget.BOTTOM -> {
                                            val jsBottom = "window.scrollTo(0, document.body.scrollHeight);"
                                            view?.evaluateJavascript(jsBottom, null)
                                        }
                                        ScrollTarget.SAVED -> {
                                            if (savedScrollFraction > 0f) {
                                                val jsScroll = """
                                                    var range = document.body.scrollHeight - window.innerHeight;
                                                    if (range > 0) {
                                                        window.scrollTo(0, range * $savedScrollFraction);
                                                    }
                                                """.trimIndent()
                                                view?.evaluateJavascript(jsScroll, null)
                                            }
                                        }
                                    }
                                }
                            }

                            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapUp(e: MotionEvent): Boolean {
                                    showControls = !showControls
                                    return super.onSingleTapUp(e)
                                }
                            })

                            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                                val totalHeight = (contentHeight * scale).toInt()
                                val viewportHeight = height
                                val scrollableHeight = totalHeight - viewportHeight

                                val scrollFraction = if (scrollableHeight > 0) scrollY.toFloat() / scrollableHeight else 0f

                                if (totalChapters > 0) {
                                    val currentGlobalIndex = currentChapterIndex + scrollFraction.coerceIn(0f, 1f)
                                    globalProgressPercent = (currentGlobalIndex / totalChapters) * 100f
                                } else if (totalChapters == 1) {
                                    globalProgressPercent = scrollFraction * 100f
                                }
                            }

                            setOnTouchListener { v, event ->
                                gestureDetector.onTouchEvent(event)
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> { dragStartY = event.y }
                                    MotionEvent.ACTION_MOVE -> {
                                        val isAtBottom = !v.canScrollVertically(1)
                                        val isAtTop = !v.canScrollVertically(-1)
                                        val currentY = event.y

                                        if (isAtBottom && (dragStartY - currentY) > OVERSCROLL_THRESHOLD) {
                                            if (!isLoading && totalChapters > 1 && currentChapterIndex < totalChapters - 1) {
                                                scrollTarget = ScrollTarget.TOP
                                                currentChapterIndex++
                                                if (totalChapters > 0) {
                                                    globalProgressPercent = (currentChapterIndex.toFloat() / totalChapters) * 100f
                                                }
                                                dragStartY = currentY
                                                return@setOnTouchListener true
                                            }
                                        }

                                        if (isAtTop && (dragStartY - currentY) < -OVERSCROLL_THRESHOLD) {
                                            if (!isLoading && totalChapters > 1 && currentChapterIndex > 0) {
                                                scrollTarget = ScrollTarget.BOTTOM
                                                currentChapterIndex--
                                                if (totalChapters > 0) {
                                                    globalProgressPercent = ((currentChapterIndex + 1f) / totalChapters) * 100f
                                                }
                                                dragStartY = currentY
                                                return@setOnTouchListener true
                                            }
                                        }
                                        if (!isAtBottom && !isAtTop) dragStartY = currentY
                                    }
                                }
                                false
                            }
                        }
                    },
                    update = { webView ->
                        webView.settings.textZoom = fontSize
                        webView.setBackgroundColor(if (isDarkMode) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())

                        // Use finalHtml which includes CSS injection
                        if (finalHtml != null && webView.tag != finalHtml) {
                            webView.tag = finalHtml
                            // CRITICAL: Base URL must point to files dir to load fonts
                            val baseUrl = "file://${context.filesDir.absolutePath}/"
                            webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "UTF-8", null)
                        }

                        webView.evaluateJavascript(getThemeJs(isDarkMode), null)
                    }
                )

                ReadingStatusBar(
                    progress = globalProgressPercent.toInt(),
                    isDarkMode = isDarkMode,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                )
            }
        }
    }

    if (showFontSheet) {
        ModalBottomSheet(onDismissRequest = { showFontSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Select Font",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn {
                    // Default Option
                    item {
                        ListItem(
                            headlineContent = { Text("Default System Font") },
                            trailingContent = {
                                if (selectedFont == null) Icon(Icons.Default.Check, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                viewModel.selectFont(null)
                                showFontSheet = false
                            }
                        )
                    }

                    // Custom Fonts
                    items(availableFonts) { fontName ->
                        ListItem(
                            headlineContent = { Text(fontName) },
                            trailingContent = {
                                if (selectedFont == fontName) Icon(Icons.Default.Check, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                viewModel.selectFont(fontName)
                                showFontSheet = false
                            }
                        )
                    }
                }

                if (availableFonts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No custom fonts imported. Go to Settings to add fonts.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterNavigation(
    currentIndex: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf("") }

    Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onPrev, enabled = currentIndex > 0) { Text("Prev") }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        inputValue = ""
                        showDialog = true
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "Ch ${currentIndex + 1} / $total",
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            }

            Button(onClick = onNext, enabled = currentIndex < total - 1) { Text("Next") }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Go to Chapter") },
            text = {
                Column {
                    Text("Enter chapter number (1 - $total):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { if (it.all { char -> char.isDigit() }) inputValue = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = inputValue.toIntOrNull()
                        if (num != null && num in 1..total) {
                            onJumpTo(num - 1)
                            showDialog = false
                        }
                    },
                    enabled = inputValue.isNotEmpty()
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

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
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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

@Composable
fun ReaderControls(
    fontSize: Int,
    isDarkMode: Boolean,
    onFontChange: (Int) -> Unit,
    onThemeToggle: () -> Unit,
    onOpenFontSheet: () -> Unit
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
                Row {
                    // Font Selection Button
                    IconButton(onClick = onOpenFontSheet) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Change Font")
                    }
                    IconButton(onClick = onThemeToggle) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
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