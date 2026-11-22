package top.ntutn.kica.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.resources.*
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.component.FluentTextField

@Composable
internal fun LoginScreen(
    busy: Boolean,
    loginError: String?,
    onLogin: (String, String) -> Unit,
) {
    var emailValue by rememberSaveable { mutableStateOf("") }
    // Credentials deliberately do not survive UI destruction or enter saved state.
    var passwordValue by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val enterCredentials = stringResource(Res.string.enter_credentials)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FluentCard(modifier = Modifier.width(400.dp).padding(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(Res.string.login_title), style = FluentTheme.typography.title)
                FluentTextField(
                    value = emailValue, onValueChange = { emailValue = it },
                    label = { Text(stringResource(Res.string.email)) }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                FluentTextField(
                    value = passwordValue, onValueChange = { passwordValue = it },
                    label = { Text(stringResource(Res.string.password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                (validationError ?: loginError)?.let { Text(it, color = FluentTheme.colors.system.critical) }
                FluentPrimaryButton(
                    onClick = {
                        if (emailValue.isBlank() || passwordValue.isBlank()) validationError = enterCredentials
                        else { validationError = null; onLogin(emailValue.trim(), passwordValue) }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) { FluentProgressRing(Modifier.size(18.dp), size = 18.dp); Spacer(Modifier.width(8.dp)) }
                    Text(stringResource(if (busy) Res.string.logging_in else Res.string.login))
                }
            }
        }
    }
}

internal fun ComicDetail.toSummary() = ComicSummary(
    id = id, title = title, author = author, coverUrl = coverUrl,
    categories = categories, tags = tags, finished = finished, likes = likes, views = views,
)

internal fun progress(comicId: String, episodeId: String, page: Int, mode: ReaderMode) = ReadingProgress(
    comicId = comicId, episodeId = episodeId, pageIndex = page, mode = mode,
    updatedAtEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
)
