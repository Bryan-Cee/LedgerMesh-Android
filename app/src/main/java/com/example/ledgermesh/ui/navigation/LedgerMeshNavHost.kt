package com.example.ledgermesh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ledgermesh.ui.analytics.AnalyticsScreen
import com.example.ledgermesh.ui.dashboard.DashboardScreen
import com.example.ledgermesh.ui.detail.TransactionDetailScreen
import com.example.ledgermesh.ui.import_center.ImportScreen
import com.example.ledgermesh.ui.ledger.LedgerScreen
import com.example.ledgermesh.ui.review.ReviewQueueScreen
import com.example.ledgermesh.ui.settings.SettingsScreen

/** Route constant for the review queue (not a bottom-nav destination). */
const val REVIEW_QUEUE_ROUTE = "review_queue"

/**
 * Central navigation host that maps each [Screen] route to its composable destination.
 */
@Composable
fun LedgerMeshNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onNavigateToTransactionDetail: (String) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.DASHBOARD.route,
        modifier = modifier
    ) {
        composable(Screen.DASHBOARD.route) {
            DashboardScreen(
                onTransactionClick = onNavigateToTransactionDetail,
                onNavigateToReview = { navController.navigate(REVIEW_QUEUE_ROUTE) }
            )
        }
        composable(Screen.LEDGER.route) {
            LedgerScreen(onTransactionClick = onNavigateToTransactionDetail)
        }
        composable(Screen.IMPORT.route) {
            ImportScreen()
        }
        composable(Screen.ANALYTICS.route) {
            AnalyticsScreen()
        }
        composable(Screen.SETTINGS.route) {
            SettingsScreen()
        }
        composable(REVIEW_QUEUE_ROUTE) {
            ReviewQueueScreen(
                onNavigateToDetail = { aggregateId ->
                    onNavigateToTransactionDetail(aggregateId)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "transaction/{aggregateId}",
            arguments = listOf(navArgument("aggregateId") { type = NavType.StringType })
        ) {
            TransactionDetailScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
