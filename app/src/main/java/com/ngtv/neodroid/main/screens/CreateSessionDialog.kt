package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import com.ngtv.neodroid.sharedComponents.ValidatedTextField
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
internal fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onSubmit: (SessionType, Map<String, String>) -> Unit,
) {
    var selectedType by remember { mutableStateOf(SessionType.NvimEmbedded) }

    val inputs: SnapshotStateMap<String, String> = remember(selectedType) {
        mutableStateMapOf<String, String>().apply {
            putAll(selectedType.createDefaultInputs())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New session")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = "Choose a session type",
                    style = MaterialTheme.typography.titleSmall,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SessionType.entries.forEach { type ->
                        FilterChip(
                            selected = type == selectedType,
                            onClick = { selectedType = type },
                            label = { Text(type.label) },
                        )
                    }
                }

                Text(
                    text = selectedType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                selectedType.fields.forEach { field ->
                    ValidatedTextField(
                        value = inputs[field.key].orEmpty(),
                        label = field.label,
                        onValueChange = { inputs[field.key] = it },
                        validator = field.validator,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedType.isValidInputs(inputs),
                onClick = { onSubmit(selectedType, inputs.toMap()) },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

internal data class SessionField(
    val key: String,
    val label: String,
    val default: String = "",
    val validator: (String) -> String? = { null },
)

internal enum class SessionType(
    val label: String,
    val description: String,
    val fields: List<SessionField> = emptyList(),
) {
    NvimEmbedded(
        label = "New Neovim",
        description = "Start a new Neovim process on the remote host.",
    ),
    NvimUnixSocket(
        label = "Existing Neovim",
        description = "Attach to a running Neovim instance over a UNIX socket. " +
            "Requires socat on the remote host.",
        fields = listOf(
            SessionField("socketpath", "Socket path") { value ->
                if (value.isBlank()) {
                    "Socket path is required"
                } else {
                    null
                }
            },
        ),
    ),
    ;

    fun createDefaultInputs(): Map<String, String> =
        fields.associate { field -> field.key to field.default }

    fun isValidInputs(inputs: Map<String, String>): Boolean = fields.all { field ->
        inputs.containsKey(field.key) && field.validator(inputs[field.key].orEmpty()) == null
    }
}
