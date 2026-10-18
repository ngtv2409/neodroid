package com.ngtv.neodroid.main.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.main.screens.AddHostScreen
import com.ngtv.neodroid.main.screens.DetailHostScreen
import com.ngtv.neodroid.main.screens.HomeScreen
import com.ngtv.neodroid.sharedComponents.PasswordInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceNavigation(viewModel: SSHHostViewModel, hostId: Int, back: () -> Unit) {
    val hostHandler by viewModel.getHostById(
        hostId,
    ).collectAsStateWithLifecycle(initialValue = null)
    // TODO: maybe provide loading feedback?
    val host = hostHandler ?: return

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {

        // TODO: support key auth
        composable("home") {
            var password by remember { mutableStateOf("") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Connect to server",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Text(
                    text = "Enter your credentials to establish a secure SSH connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Text(
                            text = host.server_name,
                            style = MaterialTheme.typography.titleLarge,
                        )

                        Text(
                            text = "${host.username}@${host.hostname}:${host.port}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Text(
                    text = "Authentication",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                )

                PasswordInput(
                    password = password,
                    onPasswordChange = { password = it },
                )

                Button(
                    onClick = {
                        // TODO: Try to connect
                    },
                    enabled = password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
