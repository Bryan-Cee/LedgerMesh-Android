package com.example.ledgermesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ledgermesh.data.AppPreferences
import com.example.ledgermesh.ui.navigation.LedgerMeshNavHost
import com.example.ledgermesh.ui.navigation.Screen
import com.example.ledgermesh.ui.theme.LedgerMeshTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkMode by appPreferences.darkModeFlow.collectAsStateWithLifecycle()
            LedgerMeshTheme(darkTheme = darkMode) {
                LedgerMeshApp()
            }
        }
    }
}

@Composable
fun LedgerMeshApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Screen.entries.forEach { screen ->
                item(
                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                    label = { Text(screen.label) },
                    selected = currentRoute == screen.route,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.DASHBOARD.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            LedgerMeshNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                onNavigateToTransactionDetail = { aggregateId ->
                    navController.navigate("transaction/$aggregateId")
                }
            )
        }
    }
}
