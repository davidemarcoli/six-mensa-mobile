package dev.davidemarcoli.sixmensa.ui.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.share.ShareActions
import dev.davidemarcoli.sixmensa.ui.components.ErrorBanner

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    restaurant: Restaurant,
    state: PdfUiState,
    onLoad: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var containerWidth by remember { mutableStateOf(0) }

    // Render at 2x the container width so the plan stays legible when zoomed in.
    LaunchedEffect(containerWidth, restaurant) {
        if (containerWidth > 0) onLoad(containerWidth * 2)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pdf_plan, restaurant.displayName)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.nav_back),
                        )
                    }
                },
                actions = {
                    state.file?.let { file ->
                        // Hidden when nothing on the device can open a PDF.
                        val externalIntent = remember(file) {
                            ShareActions.openPdfExternallyIntent(context, file)
                        }
                        externalIntent?.let { intent ->
                            IconButton(onClick = { with(ShareActions) { context.launch(intent) } }) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    stringResource(R.string.pdf_open_external),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                ShareActions.sharePdf(
                                    context = context,
                                    file = file,
                                    title = restaurant.displayName,
                                )
                            },
                        ) {
                            Icon(Icons.Default.Share, stringResource(R.string.share_menu))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .onSizeChanged { containerWidth = it.width },
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.error != null -> ErrorBanner(
                    error = state.error,
                    onRetry = { onRetry(containerWidth * 2) },
                    modifier = Modifier.padding(16.dp),
                )

                state.pages.isEmpty() -> Text(stringResource(R.string.empty_no_week))

                else -> ZoomablePages(state)
            }
        }
    }
}

/**
 * Pan/zoom over the rendered pages.
 *
 * [offset] is the top-left of the viewport expressed in *content* coordinates, paired with
 * `TransformOrigin(0, 0)`. That combination is what makes a pinch keep the point under the
 * fingers stationary — scaling around the layout centre instead zooms toward the middle of
 * the page no matter where you pinch. Offsets are clamped to the content bounds, so the
 * page can never be stranded off-screen, and vertical panning doubles as scrolling (hence
 * no separate verticalScroll, which would fight the gesture detector).
 */
@Composable
private fun ZoomablePages(state: PdfUiState) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var content by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun clamp(candidate: Offset, forScale: Float): Offset {
        val maxX = (content.width - viewport.width / forScale).coerceAtLeast(0f)
        val maxY = (content.height - viewport.height / forScale).coerceAtLeast(0f)
        return Offset(
            candidate.x.coerceIn(0f, maxX),
            candidate.y.coerceIn(0f, maxY),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val target = if (scale > MIN_ZOOM) MIN_ZOOM else DOUBLE_TAP_ZOOM
                        val candidate = if (target == MIN_ZOOM) {
                            Offset.Zero
                        } else {
                            (offset + tap / scale) - (tap / target)
                        }
                        scale = target
                        offset = clamp(candidate, target)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    // Keep the content point under the centroid fixed across the zoom.
                    val candidate =
                        (offset + centroid / scale) - (centroid / newScale + pan / newScale)
                    scale = newScale
                    offset = clamp(candidate, newScale)
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { content = it }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = -offset.x * scale
                    translationY = -offset.y * scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.pages.forEachIndexed { index, page ->
                Image(
                    bitmap = page,
                    contentDescription = stringResource(R.string.pdf_page, index + 1),
                    // Rendered at 2x the container width so it stays sharp when zoomed in,
                    // therefore it must be scaled back down to fit the viewport.
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White),
                )
            }
        }
    }
}
