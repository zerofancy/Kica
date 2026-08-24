package top.ntutn.kica.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.sha256
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.confirm
import top.ntutn.kica.resources.lock_password
import top.ntutn.kica.resources.lock_password_confirm
import top.ntutn.kica.resources.lock_password_incorrect
import top.ntutn.kica.resources.lock_password_mismatch
import top.ntutn.kica.resources.lock_password_too_short
import top.ntutn.kica.resources.lock_set_password
import top.ntutn.kica.resources.lock_unlock
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.ui.component.FluentTextField
import top.ntutn.kica.ui.PlatformBackHandler@Composable
internal fun LockScreen(
    passwordHash: String,
    onUnlock: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val incorrectMsg = stringResource(Res.string.lock_password_incorrect)

    PlatformBackHandler(enabled = true, onBack = { /* block back */ })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FluentTheme.colors.system.neutralBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(360.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Kica",
                style = FluentTheme.typography.title,
            )
            Spacer(Modifier.height(8.dp))
            FluentTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text(stringResource(Res.string.lock_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(
                    it,
                    color = FluentTheme.colors.system.critical,
                    style = FluentTheme.typography.caption,
                )
            }
            FluentPrimaryButton(
                onClick = {
                    if (password.isBlank()) return@FluentPrimaryButton
                    busy = true
                    error = null
                    val hashed = sha256(password)
                    if (hashed == passwordHash) {
                        onUnlock()
                    } else {
                        password = ""
                        error = incorrectMsg
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    FluentProgressRing(Modifier.size(18.dp), size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(Res.string.lock_unlock))
            }
        }
    }
}

@Composable
internal fun LockSettingsDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val tooShortMsg = stringResource(Res.string.lock_password_too_short)
    val mismatchMsg = stringResource(Res.string.lock_password_mismatch)
    val setPasswordTitle = stringResource(Res.string.lock_set_password)

    PlatformBackHandler(enabled = true, onBack = onDismiss)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        FluentCard(Modifier.width(400.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    setPasswordTitle,
                    style = FluentTheme.typography.subtitle,
                )
                FluentTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.lock_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FluentTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.lock_password_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        it,
                        color = FluentTheme.colors.system.critical,
                        style = FluentTheme.typography.caption,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FluentTextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    FluentPrimaryButton(
                        onClick = {
                            when {
                                password.length < 4 -> {
                                    error = tooShortMsg
                                }
                                password != confirmPassword -> {
                                    error = mismatchMsg
                                }
                                else -> onConfirm(password)
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.confirm))
                    }
                }
            }
        }
    }
}
