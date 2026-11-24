package io.github.kevin77688.uvd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.download.DownloadBus
import io.github.kevin77688.uvd.download.DownloadStatus
import io.github.kevin77688.uvd.extractor.Extractors

@Composable
fun DownloadScreen(
    link: String,
    onLinkChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDownload: () -> Unit,
    folderLabel: String?,
    onChooseFolder: () -> Unit,
) {
    val status by DownloadBus.status.collectAsState()
    val detected = remember(link) { Extractors.find(link) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    Extractors.platforms.joinToString(" · ") { it.displayName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = link,
            onValueChange = onLinkChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.link_label)) },
            placeholder = { Text(stringResource(R.string.link_placeholder)) },
            supportingText = detected?.let { match ->
                { Text(stringResource(R.string.link_detected, match.platform.displayName)) }
            },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = if (link.isNotEmpty()) {
                {
                    IconButton(onClick = { onLinkChange("") }) {
                        Icon(painterResource(R.drawable.ic_clear), contentDescription = stringResource(R.string.action_clear))
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDownload() }),
        )

        FilledTonalButton(
            onClick = onPaste,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_paste), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_paste), style = MaterialTheme.typography.titleSmall)
        }

        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_download), style = MaterialTheme.typography.titleSmall)
        }

        StatusCard(status)

        if (folderLabel == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.folder_none),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.action_choose)) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    folderLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(status: DownloadStatus) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        DownloadStatus.Idle -> Unit
        is DownloadStatus.Working -> Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status.message, style = MaterialTheme.typography.bodyMedium)
                val p = status.progress
                if (p == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        is DownloadStatus.Success -> ResultRow(
            icon = R.drawable.ic_check,
            text = pluralStringResource(R.plurals.saved_files, status.count, status.count),
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
        )
        is DownloadStatus.Failure -> ResultRow(
            icon = R.drawable.ic_error,
            text = status.message,
            container = colors.errorContainer,
            content = colors.onErrorContainer,
        )
    }
}

@Composable
private fun ResultRow(icon: Int, text: String, container: Color, content: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
