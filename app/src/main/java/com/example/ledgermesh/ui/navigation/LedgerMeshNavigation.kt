package com.example.ledgermesh.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations reachable from the bottom navigation bar.
 */
enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Default.Dashboard),
    LEDGER("ledger", "Ledger", Icons.AutoMirrored.Filled.ListAlt),
    IMPORT("import", "Import", Icons.Default.FileUpload),
    ANALYTICS("analytics", "Analytics", Icons.Default.Analytics),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}
