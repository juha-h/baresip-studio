package com.tutpro.baresip

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import java.util.GregorianCalendar
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ActiveCallBanner(
    ctx: Context,
    call: Call,
    navController: NavController,
    viewModel: ViewModel,
    modifier: Modifier = Modifier
) {
    val status by call.status
    val isOnHold = call.callOnHold.value
    val isHeldByPeer = call.showOnHoldNotice.value
    val peerUri = call.peerUri

    val contact = remember(peerUri) {
        Contact.findContact(peerUri)
    }
    val displayName = contact?.name() ?: Utils.friendlyUri(ctx, peerUri, call.ua.account)

    val greenColor = Color(0xFF2ABB86)
    val redColor = Color(0xFFEA4335)
    val yellowColor = Color(0xFFF9A825)

    val statusText = when {
        isHeldByPeer -> stringResource(R.string.call_is_on_hold)
        isOnHold -> stringResource(R.string.call_is_on_hold)
        status == "ringing" -> stringResource(R.string.ringing)
        status == "outgoing" -> stringResource(R.string.calling)
        status == "incoming" -> stringResource(R.string.incoming_call)
        status == "connected" || status == "answered" -> stringResource(R.string.connected)
        else -> status
    }

    val statusColor = when {
        isOnHold || isHeldByPeer -> yellowColor
        status == "ringing" -> greenColor
        status == "outgoing" -> yellowColor
        status == "incoming" -> greenColor
        status == "connected" || status == "answered" -> greenColor
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clickable {
                viewModel.setFocusedCall(call)
                navController.navigate("call")
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOnHold) Icons.Outlined.PauseCircle else Icons.Filled.Call,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                    if ((status == "connected" || status == "answered") && call.startTime != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CallDurationBannerTimer(call.startTime!!)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    call.terminated.value = true
                    call.hangup(487, "Request Terminated")
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = stringResource(R.string.hangup),
                    tint = redColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun CallDurationBannerTimer(startTime: GregorianCalendar) {
    var durationText by remember { mutableStateOf("") }
    LaunchedEffect(startTime) {
        while (true) {
            val elapsedMillis = System.currentTimeMillis() - startTime.timeInMillis
            val seconds = if (elapsedMillis > 0) elapsedMillis / 1000 else 0
            durationText = android.text.format.DateUtils.formatElapsedTime(seconds)
            delay(1000.milliseconds)
        }
    }
    Text(
        text = "• $durationText",
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
