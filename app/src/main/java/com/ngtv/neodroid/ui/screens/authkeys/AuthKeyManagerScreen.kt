@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.ui.screens.authkeys

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.R
import com.ngtv.neodroid.database.AuthKeyCreationState
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.database.AuthKeyViewModel
import com.ngtv.neodroid.database.KeyTransferState
import com.ngtv.neodroid.security.AuthKeyAlgorithm
import com.ngtv.neodroid.security.KeyExportFormat
import com.ngtv.neodroid.security.KeyExportRequest
import com.ngtv.neodroid.security.sshPublicKeyFingerprint
import com.ngtv.neodroid.ui.components.ActionMenuItem
import com.ngtv.neodroid.ui.components.CompactListItem
import com.ngtv.neodroid.ui.components.ConfirmDeleteDialog
import com.ngtv.neodroid.ui.components.DetailRow
import com.ngtv.neodroid.ui.components.EmptyState
import com.ngtv.neodroid.ui.components.HintText
import com.ngtv.neodroid.ui.components.ListSectionHeader
import com.ngtv.neodroid.ui.components.NeodroidTopBar
import com.ngtv.neodroid.ui.components.SectionLabel
import com.ngtv.neodroid.ui.components.SplitPaneLayout
import com.ngtv.neodroid.ui.components.ValidatedTextField
import com.ngtv.neodroid.ui.theme.Spacing
import com.ngtv.neodroid.ui.util.relativeTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuthKeyManagerScreen(viewModel: AuthKeyViewModel, onBack: () -> Unit) {
    val keys by viewModel.recentKeys.collectAsStateWithLifecycle()
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()

    var selectedKeyId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var keyPendingDeletion by remember { mutableStateOf<AuthKeyEntity?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var exportDialogKeys by remember { mutableStateOf<List<AuthKeyEntity>?>(null) }
    var pendingExport by remember { mutableStateOf<KeyExportRequest?>(null) }

    val selectedKey = keys.firstOrNull { it.id == selectedKeyId }
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.inspectImport(uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
    ) { uri ->
        val request = pendingExport
        pendingExport = null
        if (uri != null && request != null) {
            viewModel.export(request, uri)
        }
    }

    LaunchedEffect(keys) {
        val validIds = keys.map { it.id }.toSet()
        if (selectedIds.any { it !in validIds }) {
            selectedIds = selectedIds.intersect(validIds)
        }
    }

    LaunchedEffect(transferState) {
        when (val state = transferState) {
            is KeyTransferState.Finished -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearTransferState()
            }

            is KeyTransferState.Failed -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearTransferState()
            }

            else -> Unit
        }
    }

    val openCreate = {
        viewModel.resetCreationState()
        isCreating = true
        selectedKeyId = null
        selectionMode = false
        selectedIds = emptySet()
    }

    val cancelSelection = {
        selectionMode = false
        selectedIds = emptySet()
    }

    val selectKey = { key: AuthKeyEntity ->
        if (selectionMode) {
            selectedIds = if (key.id in selectedIds) {
                selectedIds - key.id
            } else {
                selectedIds + key.id
            }
        } else {
            isCreating = false
            selectedKeyId = key.id
        }
    }

    val topBarContent: @Composable () -> Unit = {
        AuthKeysTopBar(
            selectionMode = selectionMode,
            selectionCount = selectedIds.size,
            onBack = onBack,
            onAdd = openCreate,
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onSelect = {
                selectionMode = true
                selectedIds = emptySet()
                isCreating = false
                selectedKeyId = null
            },
            onCancelSelection = cancelSelection,
            onExportSelected = {
                val selected = keys.filter { it.id in selectedIds }
                if (selected.isNotEmpty()) exportDialogKeys = selected
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SplitPaneLayout(
            listPane = {
                topBarContent()
                AuthKeyList(
                    keys = keys,
                    selectedKeyId = selectedKeyId,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onKeyClick = selectKey,
                    onDelete = { keyPendingDeletion = it },
                    onExport = { exportDialogKeys = listOf(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            },
            detailPane = {
                when {
                    isCreating -> {
                        CreateAuthKeyScreen(
                            viewModel = viewModel,
                            onBack = { isCreating = false },
                            onCreated = { isCreating = false },
                        )
                    }

                    selectedKey != null -> {
                        AuthKeyDetailScreen(
                            key = selectedKey,
                            onBack = { selectedKeyId = null },
                            onRename = { displayName ->
                                viewModel.updateKey(
                                    selectedKey.copy(displayName = displayName),
                                )
                            },
                            onExport = { exportDialogKeys = listOf(selectedKey) },
                        )
                    }

                    else -> {
                        EmptyState(
                            title = "Select a key",
                            message = "Choose an authentication key from the list to " +
                                "view its details.",
                        )
                    }
                }
            },
            compactContent = {
                when {
                    isCreating -> {
                        CreateAuthKeyScreen(
                            viewModel = viewModel,
                            onBack = { isCreating = false },
                            onCreated = { isCreating = false },
                        )
                    }

                    selectedKey != null -> {
                        AuthKeyDetailScreen(
                            key = selectedKey,
                            onBack = { selectedKeyId = null },
                            onRename = { displayName ->
                                viewModel.updateKey(
                                    selectedKey.copy(displayName = displayName),
                                )
                            },
                            onExport = { exportDialogKeys = listOf(selectedKey) },
                        )
                    }

                    else -> {
                        Scaffold(
                            topBar = { topBarContent() },
                        ) { innerPadding ->
                            AuthKeyList(
                                keys = keys,
                                selectedKeyId = null,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onKeyClick = selectKey,
                                onDelete = { keyPendingDeletion = it },
                                onExport = { exportDialogKeys = listOf(it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                            )
                        }
                    }
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    keyPendingDeletion?.let { key ->
        ConfirmDeleteDialog(
            title = "Delete auth key?",
            message = "\"${key.displayName.ifBlank {
                AuthKeyAlgorithm.displayName(key.algorithm)
            }}\" will be removed. This cannot be undone.",
            onDismiss = { keyPendingDeletion = null },
            onConfirm = {
                viewModel.deleteKey(key)
                if (selectedKeyId == key.id) selectedKeyId = null
                keyPendingDeletion = null
            },
        )
    }

    exportDialogKeys?.let { keysToExport ->
        ExportKeysDialog(
            keys = keysToExport,
            onDismiss = { exportDialogKeys = null },
            onConfirm = { format, passphrase ->
                val request = KeyExportRequest(
                    keys = keysToExport,
                    format = format,
                    passphrase = passphrase,
                )
                pendingExport = request
                exportDialogKeys = null
                exportLauncher.launch(suggestedExportName(keysToExport, format))
            },
        )
    }

    when (val state = transferState) {
        is KeyTransferState.AwaitingPassphrase -> {
            ImportPassphraseDialog(
                format = state.format,
                error = state.error,
                onDismiss = { viewModel.clearTransferState() },
                onSubmit = { passphrase ->
                    viewModel.inspectImport(
                        uri = state.uri,
                        passphrase = passphrase.toCharArray(),
                        formatHint = state.format,
                    )
                },
            )
        }

        is KeyTransferState.ImportPreview -> {
            ImportPreviewDialog(
                candidates = state.candidates,
                onDismiss = { viewModel.clearTransferState() },
                onConfirm = { selected -> viewModel.importCandidates(selected) },
            )
        }

        is KeyTransferState.Working -> {
            TransferProgressDialog(message = "Working")
        }

        else -> Unit
    }
}

@Composable
private fun AuthKeysTopBar(
    selectionMode: Boolean,
    selectionCount: Int,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onSelect: () -> Unit,
    onCancelSelection: () -> Unit,
    onExportSelected: () -> Unit,
) {
    if (selectionMode) {
        NeodroidTopBar(
            title = "$selectionCount selected",
            onBack = onCancelSelection,
            actions = {
                TextButton(onClick = onCancelSelection) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = onExportSelected,
                    enabled = selectionCount > 0,
                ) {
                    Text("Export")
                }
            },
        )
    } else {
        AuthKeyTopBar(
            title = "Auth keys",
            onBack = onBack,
            onAdd = onAdd,
            onImport = onImport,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AuthKeyTopBar(
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
) {
    NeodroidTopBar(
        title = title,
        onBack = onBack,
        actions = {
            if (onEdit != null) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.semantics {
                        contentDescription = "Edit auth key name"
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text("Edit")
                }
            }
            if (onSelect != null) {
                TextButton(
                    onClick = onSelect,
                    modifier = Modifier.semantics {
                        contentDescription = "Select auth keys"
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text("Select")
                }
            }
            if (onImport != null) {
                IconButton(
                    onClick = onImport,
                    modifier = Modifier.semantics {
                        contentDescription = "Import auth key"
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_download),
                        contentDescription = null,
                    )
                }
            }
            if (onAdd != null) {
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.semantics {
                        contentDescription = "Add auth key"
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                    )
                }
            }
        },
    )
}

@Composable
private fun AuthKeyList(
    keys: List<AuthKeyEntity>,
    selectedKeyId: Int?,
    selectionMode: Boolean,
    selectedIds: Set<Int>,
    onKeyClick: (AuthKeyEntity) -> Unit,
    onDelete: (AuthKeyEntity) -> Unit,
    onExport: (AuthKeyEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item(key = "keys-header") {
            ListSectionHeader(
                title = "Keys",
                count = keys.size,
            )
        }

        if (keys.isEmpty()) {
            item(key = "keys-empty") {
                HintText("No authentication keys saved yet. Keys will appear here once created.")
            }
        } else {
            itemsIndexed(
                items = keys,
                key = { _, key -> "key-${key.id}" },
            ) { index, key ->
                AuthKeyRow(
                    key = key,
                    selected = if (selectionMode) {
                        key.id in selectedIds
                    } else {
                        key.id == selectedKeyId
                    },
                    selectionMode = selectionMode,
                    selectedForExport = key.id in selectedIds,
                    onClick = { onKeyClick(key) },
                    onDelete = { onDelete(key) },
                    onExport = { onExport(key) },
                )
                if (index < keys.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
            }
        }
    }
}

@Composable
private fun AuthKeyRow(
    key: AuthKeyEntity,
    selected: Boolean,
    selectionMode: Boolean,
    selectedForExport: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val fingerprint = remember(key.publicKey) {
        sshPublicKeyFingerprint(key.publicKey)
    }

    CompactListItem(
        headlineContent = {
            Text(
                text = key.displayName.ifBlank {
                    AuthKeyAlgorithm.displayName(key.algorithm)
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = LocalContentColor.current,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = "${AuthKeyAlgorithm.displayName(key.algorithm)} • " +
                        authKeyUsageTime(key),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        LocalContentColor.current.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (fingerprint != null) {
                    Text(
                        text = fingerprint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (selected) {
                            LocalContentColor.current.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                    )
                }
            }
        },
        leadingContent = if (selectionMode) {
            {
                Checkbox(
                    checked = selectedForExport,
                    onCheckedChange = null,
                )
            }
        } else {
            null
        },
        trailingContent = if (selectionMode) {
            null
        } else {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        },
        menuActions = if (selectionMode) {
            emptyList()
        } else {
            listOf(
                ActionMenuItem(
                    label = "Export key",
                    onClick = onExport,
                ),
                ActionMenuItem(
                    label = "Delete key",
                    destructive = true,
                    onClick = onDelete,
                ),
            )
        },
        onClick = onClick,
        selected = selected,
    )
}

@Composable
private fun CreateAuthKeyScreen(
    viewModel: AuthKeyViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var algorithm by rememberSaveable { mutableStateOf(AuthKeyAlgorithm.ED25519) }

    val creationState by viewModel.creationState.collectAsStateWithLifecycle()
    val isCreating = creationState is AuthKeyCreationState.Creating

    LaunchedEffect(creationState) {
        if (creationState is AuthKeyCreationState.Created) {
            viewModel.resetCreationState()
            onCreated()
        }
    }

    Scaffold(
        topBar = {
            AuthKeyTopBar(title = "Create auth key", onBack = onBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Text(
                text = "Generate a new SSH key pair on this device. " +
                    "The private key is encrypted in database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Name", modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs))
            ValidatedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "Display name (optional)",
                maxLength = 100,
                enabled = !isCreating,
                validator = { null },
            )

            SectionLabel(
                "Algorithm",
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
            )
            AuthKeyAlgorithm.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !isCreating,
                            role = Role.RadioButton,
                        ) { algorithm = option }
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = algorithm == option,
                        onClick = null,
                        enabled = !isCreating,
                    )
                    Column(modifier = Modifier.padding(start = Spacing.md)) {
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (creationState is AuthKeyCreationState.Failed) {
                Text(
                    text = (creationState as AuthKeyCreationState.Failed).message,
                    modifier = Modifier.padding(top = Spacing.md),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xl),
                enabled = !isCreating,
                onClick = {
                    viewModel.createKey(algorithm, displayName)
                },
            ) {
                Text(if (isCreating) "Generating…" else "Create key")
            }
        }
    }
}

@Composable
private fun AuthKeyDetailScreen(
    key: AuthKeyEntity,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
) {
    val fingerprint = remember(key.publicKey) {
        sshPublicKeyFingerprint(key.publicKey)
    }
    var showRenameDialog by remember(key.id) { mutableStateOf(false) }

    var copied by remember { mutableStateOf(false) }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AuthKeyTopBar(
                title = "Auth key",
                onBack = onBack,
                onEdit = { showRenameDialog = true },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Text(
                text = key.displayName.ifBlank {
                    AuthKeyAlgorithm.displayName(key.algorithm)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = AuthKeyAlgorithm.displayName(key.algorithm),
                modifier = Modifier.padding(top = Spacing.xxs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel(
                "Metadata",
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
            )
            DetailRow("Algorithm", key.algorithm)
            DetailRow("Fingerprint", fingerprint ?: "Unavailable")
            DetailRow(
                "Created",
                relativeTime(key.createdAt),
            )
            DetailRow(
                "Last used",
                key.lastUsedAt?.let(::relativeTime) ?: "Never",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionLabel(
                    "Public key",
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText("text", key.publicKey),
                                ),
                            )
                            copied = true
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = null,
                        tint = if (copied) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    if (copied) {
                        Text("Copied", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Copy", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            SelectionContainer {
                Text(
                    text = key.publicKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            OutlinedButton(
                onClick = onExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_upload),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(Spacing.xxs))
                Text("Export private key…")
            }

            Text(
                text = "Private key material is stored encrypted on this device. " +
                    "Exports are protected by the passphrase you choose.",
                modifier = Modifier.padding(top = Spacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showRenameDialog) {
        RenameAuthKeyDialog(
            key = key,
            onDismiss = { showRenameDialog = false },
            onConfirm = { displayName ->
                onRename(displayName)
                showRenameDialog = false
            },
        )
    }
}

@Composable
private fun RenameAuthKeyDialog(
    key: AuthKeyEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var displayName by remember(key.id) { mutableStateOf(key.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit display name")
        },
        text = {
            ValidatedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "Display name",
                maxLength = 100,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(displayName.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun authKeyUsageTime(key: AuthKeyEntity): String {
    val timestamp = key.lastUsedAt
    if (timestamp == null) {
        return "Created ${relativeTime(key.createdAt)}"
    }

    return "Used ${relativeTime(timestamp)}"
}

private fun suggestedExportName(keys: List<AuthKeyEntity>, format: KeyExportFormat): String {
    if (format == KeyExportFormat.BUNDLE || keys.size > 1) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "neodroid-keys-$stamp.ndkeys"
    }

    return when (AuthKeyAlgorithm.fromStorageValue(keys.single().algorithm)) {
        AuthKeyAlgorithm.ED25519 -> "id_ed25519"

        AuthKeyAlgorithm.RSA_4096 -> "id_rsa"

        AuthKeyAlgorithm.ECDSA_P256,
        AuthKeyAlgorithm.ECDSA_P384,
        AuthKeyAlgorithm.ECDSA_P521,
        -> "id_ecdsa"

        null -> "id_key"
    }
}

private const val EXPORT_MIME_TYPE = "application/octet-stream"
