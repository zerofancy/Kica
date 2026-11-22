package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.retry
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentPrimaryButton

@Composable
internal fun ServiceErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FluentCard(modifier) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, modifier = Modifier.weight(1f), maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            FluentPrimaryButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}
