@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.ui.screens.authkeys

import android.content.ClipData
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.ngtv.neodroid.security.AuthKeyAlgorithm
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

@Composable
fun AuthKeyManagerScreen(viewModel: AuthKeyViewModel, onBack: () -> Unit) {
    val keys by viewModel.recentKeys.collectAsStateWithLifecycle()

    var selectedKeyId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var keyPendingDeletion by remember { mutableStateOf<AuthKeyEntity?>(null) }

    val selectedKey = keys.firstOrNull { it.id == selectedKeyId }

    val openCreate = {
        viewModel.resetCreationState()
        isCreating = true
        selectedKeyId = null
    }

    SplitPaneLayout(
        listPane = {
            AuthKeyTopBar(
                title = "Auth keys",
                onBack = onBack,
                onAdd = openCreate,
            )
            AuthKeyList(
                keys = keys,
                selectedKeyId = selectedKeyId,
                onKeyClick = {
                    isCreating = false
                    selectedKeyId = it
                },
                onDelete = { keyPendingDeletion = it },
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
                    )
                }

                else -> {
                    EmptyState(
                        title = "Select a key",
                        message = "Choose an authentication key from the list to view its details.",
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
                    )
                }

                else -> {
                    Scaffold(
                        topBar = {
                            AuthKeyTopBar(
                                title = "Auth keys",
                                onBack = onBack,
                                onAdd = openCreate,
                            )
                        },
                    ) { innerPadding ->
                        AuthKeyList(
                            keys = keys,
                            selectedKeyId = null,
                            onKeyClick = { selectedKeyId = it },
                            onDelete = { keyPendingDeletion = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        )
                    }
                }
            }
        },
    )

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
}

@Composable
private fun AuthKeyTopBar(
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
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
    onKeyClick: (Int) -> Unit,
    onDelete: (AuthKeyEntity) -> Unit,
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
                    selected = key.id == selectedKeyId,
                    onClick = { onKeyClick(key.id) },
                    onDelete = { onDelete(key) },
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.7f),
            )
        },
        menuActions = listOf(
            ActionMenuItem(
                label = "Delete key",
                destructive = true,
                onClick = onDelete,
            ),
        ),
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

            Text(
                text = "Private key material is stored encrypted on this device " +
                    "and is never displayed.",
                modifier = Modifier.padding(top = Spacing.lg),
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
