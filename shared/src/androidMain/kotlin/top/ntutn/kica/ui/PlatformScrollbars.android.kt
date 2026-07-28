package top.ntutn.kica.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformHorizontalScrollbar(state: LazyListState, modifier: Modifier) = Unit

@Composable
internal actual fun PlatformHorizontalScrollbar(state: ScrollState, modifier: Modifier) = Unit

@Composable
internal actual fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier) = Unit

@Composable
internal actual fun PlatformVerticalScrollbar(state: LazyGridState, modifier: Modifier) = Unit

@Composable
internal actual fun PlatformVerticalScrollbar(state: ScrollState, modifier: Modifier) = Unit
