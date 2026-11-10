package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthManager
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelCraftTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onExportClick: (() -> Unit)? = null,
    onImportClick: (() -> Unit)? = null,
    isSyncing: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by AuthManager.currentUser.collectAsState()
    val isAuthLoading by AuthManager.isLoading.collectAsState()

    var showUserMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (isSyncing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = ElectricBlue
                        )
                    }
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            } else {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Default.ElectricMeter,
                        contentDescription = "PanelCraft",
                        tint = ElectricBlue
                    )
                }
            }
        },
        actions = {
            onImportClick?.let { onImport ->
                IconButton(
                    onClick = onImport,
                    modifier = Modifier.testTag("topbar_import_button")
                ) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = "Import Schedule / Panel",
                        tint = ElectricBlue
                    )
                }
            }

            onExportClick?.let { onExport ->
                IconButton(
                    onClick = onExport,
                    modifier = Modifier.testTag("topbar_export_button")
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Export Report",
                        tint = ElectricBlue
                    )
                }
            }

            // Google Auth Status / Profile Icon
            Box {
                if (currentUser != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue)
                            .clickable { showUserMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.displayName?.firstOrNull()?.toString()?.uppercase() ?: "U",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                AuthManager.signInWithGoogle(context)
                            }
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("google_signin_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign In", fontSize = 12.sp)
                        }
                    }
                }

                DropdownMenu(
                    expanded = showUserMenu,
                    onDismissRequest = { showUserMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(currentUser?.displayName ?: "User", fontWeight = FontWeight.Bold)
                                Text(currentUser?.email ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Cloud Sync: Active", fontSize = 11.sp, color = EmeraldSuccess, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {}
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Sign Out") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        onClick = {
                            showUserMenu = false
                            AuthManager.signOut()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
