package com.ngtv.neodroid.ui.screens.authkeys

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.security.ImportCandidate
import com.ngtv.neodroid.security.KeyExportFormat
import com.ngtv.neodroid.security.KeyFileFormat
import com.ngtv.neodroid.ui.components.PasswordInput
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
fun ExportKeysDialog(
    keys: List<AuthKeyEntity>,
    onDismiss: () -> Unit,
    onConfirm: (format: KeyExportFormat, passphrase: String?) -> Unit,
) {
    val multipleKeys = keys.size > 1
    var format by remember(keys) {
        mutableStateOf(if (multipleKeys) KeyExportFormat.BUNDLE else KeyExportFormat.OPENSSH)
    }
    var usePassphrase by remember(keys) { mutableStateOf(true) }
    var passphrase by remember(keys) { mutableStateOf("") }
    var confirmation by remember(keys) { mutableStateOf("") }
    var acknowledged by remember(keys) { mutableStateOf(false) }

    val protectsPassphrase = format == KeyExportFormat.BUNDLE || usePassphrase
    val passphraseTooShort = protectsPassphrase && passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = protectsPassphrase && passphrase != confirmation
    val needsUnencryptedAcknowledgement =
        format == KeyExportFormat.OPENSSH && !usePassphrase && !acknowledged

    val canExport =
        !passphraseTooShort &&
            !mismatch &&
            !needsUnencryptedAcknowledgement &&
            (!protectsPassphrase || passphrase.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (keys.size == 1) "Export private key" else "Export ${keys.size} keys",
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = if (multipleKeys) {
                        "Selected keys will be stored in one encrypted Neodroid bundle."
                    } else {
                        "Choose how the private key should be written."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!multipleKeys) {
                    ExportFormatOption(
                        title = "OpenSSH private key",
                        detail = "Usable with standard SSH clients",
                        selected = format == KeyExportFormat.OPENSSH,
                        onClick = { format = KeyExportFormat.OPENSSH },
                    )
                    ExportFormatOption(
                        title = "Neodroid key bundle",
                        detail = "Encrypted archive for backups",
                        selected = format == KeyExportFormat.BUNDLE,
                        onClick = { format = KeyExportFormat.BUNDLE },
                    )
                }

                if (format == KeyExportFormat.OPENSSH) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) {
                                usePassphrase = !usePassphrase
                            }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = usePassphrase,
                            onCheckedChange = null,
                        )
                        Text(
                            text = "Protect with a passphrase",
                            modifier = Modifier.padding(start = Spacing.sm),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (protectsPassphrase) {
                    PasswordInput(
                        password = passphrase,
                        onPasswordChange = { passphrase = it },
                        label = "Passphrase",
                    )
                    PasswordInput(
                        password = confirmation,
                        onPasswordChange = { confirmation = it },
                        label = "Confirm passphrase",
                    )

                    when {
                        passphraseTooShort -> {
                            DialogError(
                                "Use at least $MIN_PASSPHRASE_LENGTH characters.",
                            )
                        }

                        mismatch -> DialogError("Passphrases do not match.")
                    }

                    Text(
                        text = "If you lose this passphrase the exported key cannot be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "The private key will be written without encryption. " +
                            "Anyone who can read the file can use the key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) {
                                acknowledged = !acknowledged
                            }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = acknowledged,
                            onCheckedChange = null,
                        )
                        Text(
                            text = "I understand the risk",
                            modifier = Modifier.padding(start = Spacing.sm),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        format,
                        if (protectsPassphrase) passphrase else null,
                    )
                },
                enabled = canExport,
            ) {
                Text(if (protectsPassphrase) "Choose file…" else "Export unencrypted…")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ExportFormatOption(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = Spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ImportPassphraseDialog(
    format: KeyFileFormat,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    val canSubmit = passphrase.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Unlock key file")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = when (format) {
                        KeyFileFormat.BUNDLE -> "Enter the passphrase for this Neodroid bundle."
                        else -> "Enter the passphrase for this private key."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordInput(
                    password = passphrase,
                    onPasswordChange = { passphrase = it },
                    label = "Passphrase",
                )
                if (error != null) {
                    DialogError(error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(passphrase) },
                enabled = canSubmit,
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ImportPreviewDialog(
    candidates: List<ImportCandidate>,
    onDismiss: () -> Unit,
    onConfirm: (List<ImportCandidate>) -> Unit,
) {
    val selected = remember(candidates) {
        mutableStateListOf<Boolean>().apply {
            addAll(candidates.map { !it.alreadyExists }.ifEmpty { listOf(false) })
        }
    }
    val names = remember(candidates) {
        mutableStateMapOf<Int, String>().apply {
            candidates.forEachIndexed { index, candidate ->
                put(index, candidate.displayName)
            }
        }
    }

    val selectedCandidates = candidates.indices
        .filter { index -> selected.getOrElse(index) { false } }
        .map { index ->
            candidates[index].copy(
                displayName = names[index].orEmpty().trim(),
            )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (candidates.size == 1) "Import key" else "Import ${candidates.size} keys",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (candidates.any { it.alreadyExists }) {
                    Text(
                        text = "Keys marked as already saved use the same fingerprint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    itemsIndexed(
                        items = candidates,
                        key = { index, candidate ->
                            "$index-${candidate.fingerprint}"
                        },
                    ) { index, candidate ->
                        ImportCandidateRow(
                            candidate = candidate,
                            selected = selected.getOrElse(index) { false },
                            name = names[index].orEmpty(),
                            onSelectedChange = { checked ->
                                if (index in selected.indices) selected[index] = checked
                            },
                            onNameChange = { names[index] = it },
                        )
                        if (index < candidates.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCandidates) },
                enabled = selectedCandidates.isNotEmpty() &&
                    selectedCandidates.all { it.displayName.isNotBlank() },
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ImportCandidateRow(
    candidate: ImportCandidate,
    selected: Boolean,
    name: String,
    onSelectedChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onSelectedChange(!selected) }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm),
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 100) onNameChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
            )
            Text(
                text = candidate.algorithm.displayName,
                modifier = Modifier.padding(top = Spacing.xxs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.fingerprint != null) {
                Text(
                    text = candidate.fingerprint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (candidate.alreadyExists) {
                Text(
                    text = "Already saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun TransferProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(message)
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CircularProgressIndicator()
                Text("Please wait…")
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DialogError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

private const val MIN_PASSPHRASE_LENGTH = 8
