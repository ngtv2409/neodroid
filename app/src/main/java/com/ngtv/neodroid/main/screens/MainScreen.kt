@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.main.screens

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.sharedComponents.ListItemCard

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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenHostAdd() },
            ) {
                Text("+")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(2.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                if (hasRunningSession) {
                    Button(
                        onClick = onBackToSession,
                    ) {
                        Text("Back to session")
                    }
                }
                Text("Hosts", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = hosts,
                        key = { host -> host.id },
                    ) { host ->
                        ListItemCard(
                            headlineContent = { Text(host.server_name) },
                            supportingContent = {
                                Text("${host.username}@${host.hostname} • ${host.port}")
                            },
                            onClick = {
                                onOpenDetail(host.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
