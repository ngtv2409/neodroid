package com.ngtv.neodroid

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.*
import androidx.compose.ui.text.style.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.*
import androidx.compose.animation.core.*

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.activity.viewModels

/* Handle Main screen, add and manage SSH hosts */

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val database by lazy {
        AppDatabase.getInstance(applicationContext)
    }

    private val repository by lazy {
        SSHHostRepository(database.SSHHostDao())
    }

    private val viewModelFactory by lazy {
        SSHHostViewModelFactory(repository)
    }

    private val sshHostViewModel: SSHHostViewModel by viewModels {
        viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AppNavigation(sshHostViewModel)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: SSHHostViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(viewModel,
                onOpenHostAdd = { navController.navigate("hosts/new") }
            )
        }
        composable("hosts/new") {
            AddHostScreen(onSubmit = {host -> viewModel.addHost(host)}, back = {
                navController.popBackStack()
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: SSHHostViewModel, onOpenHostAdd: () -> Unit) {
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenHostAdd() }
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Hosts", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = hosts,
                        key = { host -> host.id }
                    ) { host ->
                        SshHostItem(host)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHostScreen(onSubmit: (SSHHostViewModel.InputModel) -> Unit, back: () -> Unit) {
    var server_name by remember { mutableStateOf("") }
    var hostname by remember { mutableStateOf("") }
    var port_str by remember { mutableStateOf("22") }
    var server_signature by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val server_name_validator = { value: String ->
        when {
            value.isBlank() -> "Server name is required"
            else -> null
        }
    }
    val host_name_validator = { value: String ->
        when {
            value.isBlank() -> "Host name is required"
            else -> null
        }
    }
    val port_validator = { value: String ->
        val conv = value.toIntOrNull();
        when {
            value.isBlank() -> "Port is required"
            conv == null -> "Port must be an integer between 1 and 65535"
            !(conv in 1..65535) -> "Port must be an integer between 1 and 65535"
            else -> null
        }
    }
    val username_validator = { value: String ->
        when {
            value.isBlank() -> "Username is required"
            else -> null
        }
    }

    val canSubmit = server_name_validator(server_name) == null &&
                    host_name_validator(hostname) == null &&
                    port_validator(port_str) == null &&
                    username_validator(username) == null

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("Add SSH Host")
                }
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ValidatedTextField(
                    value = server_name,
                    onValueChange = { server_name = it },
                    label = "Server name*",
                    maxLength = 100,
                    validator = server_name_validator
                )
                ValidatedTextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = "Host name*",
                    maxLength = null,
                    validator = host_name_validator
                )
                ValidatedTextField(
                    value = port_str,
                    onValueChange = {
                        port_str = it
                    },
                    label = "Port*",
                    maxLength = null,
                    validator =  port_validator
                )
                ValidatedTextField(
                    value = server_signature,
                    onValueChange = { server_signature = it },
                    label = "Server signature (optional)",
                    maxLength = null,
                    validator = { value ->
                        null // optional
                    }
                )
                ValidatedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username",
                    maxLength = null,
                    validator = username_validator
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = canSubmit,
                        onClick = {
                            onSubmit(SSHHostViewModel.InputModel(
                                name = server_name.trim(),
                                host_name = hostname.trim(),
                                port = port_str.trim().toInt(),
                                server_signature = if (server_signature.trim().isBlank()) {null} else {server_signature.trim()},
                                username = username.trim()
                            ))
                            back()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    validator: (String) -> String? = { null },
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    var hasBeenFocused by rememberSaveable { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(false) }

    val error = validator(value)
    val showError = error != null && touched

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (maxLength == null || newValue.length <= maxLength) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    hasBeenFocused = true
                } else if (hasBeenFocused) {
                    touched = true
                }
            },
        singleLine = singleLine,
        enabled = enabled,
        isError = showError,
        supportingText = {
            when {
                showError -> {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                maxLength != null -> {
                    Text("${value.length}/$maxLength characters")
                }
            }
        }
    )
}

@Composable
fun SshHostItem(
    host: SSHHostEntity,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        ListItem(
            headlineContent = { Text(host.name) },
            supportingContent = { Text("${host.username}@${host.host_name} • ${host.port}") },
            leadingContent = {
                Text("⟩")
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}
