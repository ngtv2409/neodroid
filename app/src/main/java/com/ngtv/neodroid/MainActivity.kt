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
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
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
                onClick = {  }
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(2.dp)
        ) {
        }
    }
}
