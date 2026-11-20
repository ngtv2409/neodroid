package com.ngtv.neodroid.ui.screens.authkeys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.R
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
fun AuthKeySelector(
    keys: List<AuthKeyEntity>,
    selectedKeyId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String = "Password authentication",
    noneDetail: String = "No key selected",
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedKey = keys.firstOrNull { it.id == selectedKeyId }
    val hasKeys = keys.isNotEmpty()

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Authentication key" },
            onClick = { expanded = true },
            enabled = hasKeys,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = if (hasKeys) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !hasKeys -> "No auth keys available"
                            selectedKey != null -> authKeyDisplayName(selectedKey)
                            else -> noneLabel
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasKeys) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            !hasKeys -> "Create a key from Auth keys"
                            selectedKey != null -> authKeySummary(selectedKey)
                            else -> noneDetail
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasKeys) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        modifier = Modifier.padding(start = Spacing.sm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded && hasKeys,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 360.dp),
        ) {
            AuthKeyMenuItem(
                label = noneLabel,
                detail = noneDetail,
                selected = selectedKeyId == null,
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            keys.forEach { key ->
                AuthKeyMenuItem(
                    label = authKeyDisplayName(key),
                    detail = authKeySummary(key),
                    selected = key.id == selectedKeyId,
                    onClick = {
                        onSelect(key.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthKeyMenuItem(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClick = onClick,
        leadingIcon = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
    )
}
