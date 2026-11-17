package com.ngtv.neodroid.main.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ngtv.neodroid.AppContainer
import com.ngtv.neodroid.database.AuthKeyViewModel
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.main.screens.AddHostScreen
import com.ngtv.neodroid.main.screens.AuthKeyManagerScreen
import com.ngtv.neodroid.main.screens.ConnectionsScreen
import com.ngtv.neodroid.main.screens.DetailHostScreen
import com.ngtv.neodroid.main.screens.HomeScreen
import com.ngtv.neodroid.main.screens.SshSessionScreen
import com.ngtv.neodroid.main.screens.WorkspacePrepareClient
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.sshUtils.WorkspaceSession
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(container: AppContainer, sessionManager: SshSessionManager) {
    val viewModel: SSHHostViewModel = viewModel(factory = container.sshHostViewModelFactory)
    val authKeyViewModel: AuthKeyViewModel =
        viewModel(factory = container.authKeyViewModelFactory)
    val authKeys by authKeyViewModel.keys.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                sessionManager = sessionManager,
                onOpenHostAdd = { navController.navigate("hosts/new") },
                onOpenDetail = { id -> navController.navigate("hosts/details/$id") },
                onConnectHost = { id -> navController.navigate("workspace/$id") },
                onOpenConnection = { connectionId ->
                    navController.navigate("workspace/session/$connectionId")
                },
                onShowAllConnections = {
                    navController.navigate("connections")
                },
                onOpenAuthKeys = {
                    navController.navigate("auth-keys")
                },
                authKeys = authKeys,
            )
        }
        composable("auth-keys") {
            AuthKeyManagerScreen(
                viewModel = authKeyViewModel,
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable("connections") {
            ConnectionsScreen(
                sessionManager = sessionManager,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenConnection = { connectionId ->
                    navController.navigate("workspace/session/$connectionId")
                },
            )
        }
        composable("hosts/new") {
            AddHostScreen(
                onSubmit = { host: SSHHostViewModel.InputModel ->
                    viewModel.addHost(host)
                },
                back = dropUnlessResumed {
                    navController.popBackStack()
                },
                authKeys = authKeys,
            )
        }
        composable("hosts/details/{hostId}") { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId")?.toInt()
            if (hostId == null) {
                navController.popBackStack()
            } else {
                DetailHostScreen(
                    viewModel = viewModel,
                    hostId = hostId,
                    onBack = dropUnlessResumed { navController.popBackStack() },
                    onConnect = {
                        navController.navigate("workspace/$hostId")
                    },
                    authKeys = authKeys,
                )
            }
        }

        composable("workspace/{hostId}") { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId")?.toInt()
            if (hostId == null) {
                navController.popBackStack()
            } else {
                val hostHandler by viewModel.getHostById(
                    hostId,
                ).collectAsStateWithLifecycle(initialValue = null)
                val host = hostHandler ?: return@composable
                WorkspacePrepareClient(
                    host = host,
                    sessionManager = sessionManager,
                    authKeyMaterial = container.authKeyMaterial,
                    authKeys = authKeys,
                    onBack = dropUnlessResumed { navController.popBackStack() },
                    onConnect = { connectionId ->
                        navController.navigate("workspace/session/$connectionId") {
                            popUpTo("home") {
                                inclusive = false
                            }
                        }
                    },
                    onAuthKeyUsed = { keyId ->
                        authKeyViewModel.markKeyUsed(keyId)
                    },
                    onOpenAuthKeys = {
                        navController.navigate("auth-keys")
                    },
                    onTrustServer = { fingerprint ->
                        viewModel.updateHostKey(host.id, fingerprint)
                    },
                    onDiscardHostKey = {
                        viewModel.clearHostKey(host.id)
                    },
                )
            }
        }
        composable("workspace/session/{connectionId}") { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getString("connectionId")
            if (connectionId == null) {
                navController.popBackStack()
            } else {
                WorkspaceSessionDestination(
                    sessionManager = sessionManager,
                    connectionId = connectionId,
                    onBack = dropUnlessResumed {
                        navController.popBackStack("home", inclusive = false)
                    },
                    onMissing = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceSessionDestination(
    sessionManager: SshSessionManager,
    connectionId: String,
    onBack: () -> Unit,
    onMissing: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var session by remember(connectionId) {
        mutableStateOf<WorkspaceSession?>(null)
    }

    var sessionError by remember(connectionId) {
        mutableStateOf(false)
    }

    LaunchedEffect(sessionManager, connectionId) {
        try {
            session = sessionManager.getSession(connectionId)
            if (session == null) {
                sessionError = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sessionError = true
        }
    }

    when {
        session != null -> {
            SshSessionScreen(session!!, onBack = onBack)
        }

        sessionError -> {
            LaunchedEffect(Unit) {
                onMissing()
            }
        }
    }
}
