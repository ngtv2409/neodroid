package com.ngtv.neodroid.main.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ngtv.neodroid.main.screens.SshSessionScreen
import com.ngtv.neodroid.main.screens.WorkspacePrepareClient
import com.ngtv.neodroid.sshUtils.SshSessionManager
import kotlinx.coroutines.CancellationException
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(viewModel: SSHHostViewModel, sessionManager: SshSessionManager) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(
                viewModel,
                onOpenHostAdd = { navController.navigate("hosts/new") },
                onOpenDetail = { id -> navController.navigate("hosts/details/$id") },
            )
        }
        composable("hosts/new") {
            AddHostScreen(onSubmit = { host: SSHHostViewModel.InputModel ->
                viewModel.addHost(host)
            }, back = {
                navController.popBackStack()
            })
        }
        composable("hosts/details/{hostId}") { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId")?.toInt()
            if (hostId == null) {
                navController.popBackStack()
            } else {
                DetailHostScreen(viewModel, hostId, onConnect = {
                    navController.navigate("workspace/$hostId")
                })
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
                WorkspacePrepareClient(host, sessionManager) {
                    navController.navigate("workspace/session") {
                        popUpTo("workspace/$hostId") {
                            inclusive = true
                        }
                    }
                }
            }
        }
        composable("workspace/session") {
            var session by remember {
                mutableStateOf<Session?>(null)
            }

            var sessionError by remember {
                mutableStateOf(false)
            }

            LaunchedEffect(sessionManager) {
                try {
                    session = sessionManager.getSession()
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
                session != null -> SshSessionScreen(session!!)

                sessionError -> {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}
