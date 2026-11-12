@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.sharedComponents.CompactListItem
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
fun HomeScreen(
    viewModel: SSHHostViewModel,
    onOpenHostAdd: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    hasRunningSession: Boolean,
    onBackToSession: () -> Unit,
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("Neodroid")
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (hasRunningSession) {
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    onClick = onBackToSession,
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Back to session",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "A session is currently running",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.lg,
                        end = Spacing.sm,
                        top = Spacing.xs,
                        bottom = Spacing.xs,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hosts",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenHostAdd) {
                    Text("+ Add")
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = hosts,
                    key = { _, host -> host.id },
                ) { index, host ->
                    CompactListItem(
                        headlineContent = {
                            Text(
                                text = host.server_name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "${host.username}@${host.hostname} • ${host.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            onOpenDetail(host.id)
                        },
                    )
                    if (index < hosts.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                    }
                }
            }
        }
    }
}
