package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(ctx: Context, viewModel: ViewModel, navController: NavController) {
    val aor by viewModel.selectedAor.collectAsState()
    val accountUpdate by viewModel.accountUpdate.collectAsState()
    val isDialpadVisible by viewModel.isDialpadVisible.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showVmIcon = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) Account.ofAor(aor)?.vmUri?.isNotEmpty() ?: false else false
    }
    val hasNewVoicemail = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) (Account.ofAor(aor)?.vmNew ?: 0) > 0 else false
    }
    val isMobile = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) Account.ofAor(aor)?.isMobile ?: false else false
    }
    val hasUnreadMessages = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) Account.ofAor(aor)?.unreadMessages ?: false else false
    }
    val hasMissedCalls = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) Account.ofAor(aor)?.missedCalls ?: false else false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = if (showVmIcon) 16.dp else 32.dp, end = if (showVmIcon) 16.dp else 32.dp, bottom = 12.dp)
            .shadow(12.dp, RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(32.dp))
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voicemail (if configured on active account)
            if (showVmIcon) {
                BottomNavItem(
                    icon = Icons.Filled.Voicemail,
                    contentDescription = "Voicemail",
                    isActive = false,
                    hasBadge = hasNewVoicemail,
                    onClick = {
                        val ua = UserAgent.ofAor(aor) ?: return@BottomNavItem
                        val acc = ua.account
                        if (acc.vmUri.isNotEmpty()) {
                            val intent = Intent(ctx, MainActivity::class.java).apply {
                                putExtra("uap", ua.uap)
                                putExtra("peer", acc.vmUri)
                            }
                            handleIntent(ctx, viewModel, intent, "call")
                        }
                    }
                )
            }

            // Dialer / Keypad
            BottomNavItem(
                icon = Icons.Filled.Dialpad,
                contentDescription = "Dialpad",
                isActive = (currentRoute == "main" || currentRoute == null) && isDialpadVisible,
                onClick = {
                    if (currentRoute != "main") {
                        navController.navigate("main")
                    }
                    if (!isDialpadVisible) {
                        viewModel.toggleDialpadVisibility()
                    }
                }
            )

            // Contacts
            BottomNavItem(
                icon = Icons.Filled.Person,
                contentDescription = "Contacts",
                isActive = currentRoute == "contacts",
                onClick = {
                    if (currentRoute != "contacts") {
                        navController.navigate("contacts")
                    }
                }
            )

            // Call History
            BottomNavItem(
                icon = Icons.Filled.History,
                contentDescription = "History",
                isActive = currentRoute?.startsWith("calls") == true,
                hasBadge = hasMissedCalls,
                onClick = {
                    if (currentRoute?.startsWith("calls") != true) {
                        navController.navigate("calls/$aor")
                    }
                }
            )

            // Messages / Chats
            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Messages",
                isActive = currentRoute?.startsWith("chats") == true,
                hasBadge = hasUnreadMessages,
                onClick = {
                    if (isMobile && !Utils.isDefaultSmsApp(ctx)) {
                        Toast.makeText(ctx, R.string.enable_default_messaging, Toast.LENGTH_LONG).show()
                        return@BottomNavItem
                    }
                    if (currentRoute?.startsWith("chats") != true) {
                        navController.navigate("chats/$aor")
                    }
                }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    hasBadge: Boolean = false,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.TopEnd) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
                interactionSource = remember { MutableInteractionSource() }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (hasBadge) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 4.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
    }
}
