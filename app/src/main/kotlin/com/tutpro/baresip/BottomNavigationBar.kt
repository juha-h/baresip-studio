package com.tutpro.baresip

import android.content.Context
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
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(ctx: Context, viewModel: ViewModel, navController: NavController) {

    val aor by viewModel.selectedAor.collectAsState()
    val isDialpadVisible by viewModel.isDialpadVisible.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 48.dp, end = 48.dp, bottom = 12.dp)
            .shadow(16.dp, RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(32.dp))
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dialer
            BottomNavItem(
                iconId = R.drawable.ic_numpad_new,
                isActive = currentRoute == "main" && isDialpadVisible,
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
                iconId = R.drawable.ic_contacts_new,
                isActive = currentRoute == "contacts",
                onClick = { if (currentRoute != "contacts") navController.navigate("contacts") }
            )
            // History
            BottomNavItem(
                icon = Icons.Filled.History,
                isActive = currentRoute?.startsWith("calls") == true,
                onClick = { 
                    if (aor.isNotEmpty() && currentRoute?.startsWith("calls") != true) {
                        navController.navigate("calls/$aor")
                    }
                }
            )
            // Messages
            BottomNavItem(
                iconId = R.drawable.ic_message_new,
                isActive = currentRoute?.startsWith("chats") == true,
                onClick = {
                    if (aor.isNotEmpty() && currentRoute?.startsWith("chats") != true) {
                        navController.navigate("chats/$aor")
                    }
                }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    iconId: Int? = null,
    icon: ImageVector? = null,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val painter = if (iconId != null) painterResource(id = iconId) else null
    
    if (isActive) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White, shape = CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick, 
            modifier = Modifier.size(52.dp),
            interactionSource = remember { MutableInteractionSource() }
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
