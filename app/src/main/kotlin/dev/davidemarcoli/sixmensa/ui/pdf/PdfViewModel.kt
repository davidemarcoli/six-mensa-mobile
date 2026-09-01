package dev.davidemarcoli.sixmensa.ui.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.PdfRepository
import dev.davidemarcoli.sixmensa.data.remote.ApiError
import dev.davidemarcoli.sixmensa.data.remote.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PdfUiState(
    val isLoading: Boolean = true,
    val pages: List<ImageBitmap> = emptyList(),
    val file: File? = null,
    val error: ApiError? = null,
)

class PdfViewModel(
    private val pdfRepository: PdfRepository,
    private val renderer: PdfPageRenderer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private var lastWidth = 0
    private var inFlight: Job? = null

    fun load(restaurant: Restaurant, targetWidth: Int) {
        if (targetWidth <= 0) return
        // Re-render on a meaningful width change (rotation), but not on every recomposition.
        if (targetWidth == lastWidth && (_uiState.value.pages.isNotEmpty() || inFlight?.isActive == true)) {
            return
        }
        lastWidth = targetWidth
        // Layout can settle in two passes, firing load twice before any state lands. Without
        // this, two downloads race on the same temp file and can corrupt the PDF.
        inFlight?.cancel()

        inFlight = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val links = pdfRepository.links()
            val url = links.getOrNull()?.let { pdfRepository.linkFor(it, restaurant) }

            if (url == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = links.exceptionOrNull()
                            ?.let { t -> (t as? ApiException)?.error }
                            ?: ApiError.MenuNotFound,
                    )
                }
                return@launch
            }

            pdfRepository.download(restaurant, url)
                .onSuccess { file ->
                    runCatching { renderer.render(file, targetWidth) }
                        .onSuccess { pages ->
                            _uiState.update {
                                it.copy(isLoading = false, pages = pages, file = file)
                            }
                        }
                        .onFailure { throwable ->
                            _uiState.update {
                                it.copy(isLoading = false, error = ApiError.Malformed(throwable))
                            }
                        }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, error = ApiError.Network) }
                }
        }
    }

    fun retry(restaurant: Restaurant, targetWidth: Int) {
        val width = targetWidth.takeIf { it > 0 } ?: lastWidth
        lastWidth = 0
        _uiState.value = PdfUiState()
        load(restaurant, width)
    }
}
