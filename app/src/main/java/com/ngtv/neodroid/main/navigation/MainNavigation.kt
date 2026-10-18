package com.ngtv.neodroid.main.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.main.screens.AddHostScreen
import com.ngtv.neodroid.main.screens.DetailHostScreen
import com.ngtv.neodroid.main.screens.HomeScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(viewModel: SSHHostViewModel) {
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
                    navController.navigate("workspace/${hostId}")
                })
            }
        }

        composable("workspace/{hostId}") { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId")?.toInt()
            if (hostId == null) {
                navController.popBackStack()
            } else {
                WorkspaceNavigation(viewModel, hostId, back = { navController.popBackStack() })
            }
        }
    }
}
