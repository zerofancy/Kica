package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.HyperlinkButton
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.TextField
import io.github.composefluent.surface.Card

/**
 * Kica 使用的 Fluent 控件适配层。
 *
 * 业务页面不应重新引入 Material 控件；少量参数适配集中放在这里，
 * 这样页面仍可保持清晰，同时完整获得 Fluent 的悬停、按压、焦点和描边状态。
 */
@Composable
internal fun FluentPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    AccentButton(
        onClick = onClick,
        modifier = modifier,
        disabled = !enabled,
        content = content,
    )
}

@Composable
internal fun FluentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        disabled = !enabled,
        content = content,
    )
}

@Composable
internal fun FluentTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    HyperlinkButton(
        onClick = onClick,
        modifier = modifier,
        disabled = !enabled,
        content = content,
    )
}

@Composable
internal fun FluentIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconOnly: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    SubtleButton(
        onClick = onClick,
        modifier = modifier,
        disabled = !enabled,
        iconOnly = iconOnly,
        content = content,
    )
}

@Composable
internal fun FluentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        header = label,
        placeholder = placeholder,
    )
}

@Composable
internal fun FluentChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PillButton(
        selected = selected,
        onSelectedChanged = { onClick() },
        modifier = modifier,
        disabled = !enabled,
        content = label,
    )
}

@Composable
internal fun FluentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Card(modifier = modifier, content = content)
    } else {
        Card(onClick = onClick, modifier = modifier, content = content)
    }
}

@Composable
internal fun FluentProgressRing(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    ProgressRing(modifier = modifier, size = size)
}

@Composable
internal fun FluentProgressBar(
    modifier: Modifier = Modifier,
) {
    ProgressBar(modifier = modifier)
}

@Composable
internal fun FluentProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    ProgressBar(progress = progress, modifier = modifier)
}

@Composable
internal fun FluentScaffold(
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        topBar()
        Box(Modifier.weight(1f).fillMaxSize()) {
            content(PaddingValues())
        }
    }
}
