package top.ntutn.kica.ui

import top.ntutn.kica.model.ComicSummary

internal data class RandomComicsUiState(
    val items: List<ComicSummary>? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    fun startLoading(): RandomComicsUiState = copy(
        isLoading = true,
        errorMessage = null,
    )

    fun loadSuccess(items: List<ComicSummary>): RandomComicsUiState = RandomComicsUiState(
        items = items,
        isLoading = false,
    )

    fun loadFailure(message: String): RandomComicsUiState = copy(
        isLoading = false,
        errorMessage = message,
    )
}
