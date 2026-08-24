package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.confirm
import top.ntutn.kica.resources.download_translation_model
import top.ntutn.kica.resources.download_translation_model_message
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.ui.PlatformBackHandler


@Composable
internal fun ModelDownloadConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true, onBack = onDismiss)
        FluentCard(Modifier.width(440.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(Res.string.download_translation_model),
                    style = FluentTheme.typography.subtitle,
                )
                Text(stringResource(Res.string.download_translation_model_message))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FluentTextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    FluentPrimaryButton(onClick = onConfirm) {
                        Text(stringResource(Res.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingCard(title: String, content: @Composable () -> Unit) {
    FluentCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = FluentTheme.typography.bodyStrong)
            content()
        }
    }
}
