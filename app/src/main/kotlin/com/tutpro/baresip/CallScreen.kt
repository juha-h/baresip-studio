package com.tutpro.baresip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tutpro.baresip.BaresipService.Companion.uas
import kotlinx.coroutines.delay

fun NavGraphBuilder.callScreenRoute(navController: NavController, viewModel: ViewModel) {
    composable("call") {
        CallScreen(navController, viewModel)
    }
}

@Composable
private fun CallScreen(navController: NavController, viewModel: ViewModel) {
    val ctx = LocalContext.current
    val selectedAor by viewModel.selectedAor.collectAsState()
    val ua = uas.value.find { it.account.aor == selectedAor }
    val focusedCall by viewModel.focusedCall.collectAsState()
    val calls by viewModel.calls.collectAsState()
    
    // Find the active call for this UA or use focused call
    val call = ua?.currentCall() ?: focusedCall ?: calls.lastOrNull()
    val status = call?.status?.value ?: "idle"

    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val micIcon by viewModel.micIcon.collectAsState()
    val isMicMuted = remember(micIcon) { micIcon == Icons.Filled.MicOff }

    // Navigation Fix: Return to main if no calls are active
    LaunchedEffect(calls) {
        if (calls.isEmpty()) {
            Log.d("CallScreen", "No active calls, returning to main")
            navController.navigate("main") {
                popUpTo("main") { inclusive = true }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status: On call
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = when (status) {
                        "incoming" -> "Incoming call"
                        "outgoing", "answered" -> "Outgoing call"
                        else -> "On call"
                    },
                    color = Color(0xFF2ABB86),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF2ABB86), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Avatar
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White, CircleShape)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2ABB86), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Name and Number
            Text(
                text = if (call != null) Utils.friendlyUri(ctx, call.peerUri, call.ua.account) else "Unknown",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = call?.peerUri?.substringAfter(":") ?: "",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timer or Ringing Animation
            if (call != null && status != "incoming") {
                if (status == "outgoing" || status == "answered") {
                    var dots by remember { mutableStateOf(".") }
                    LaunchedEffect(Unit) {
                        while (true) {
                            dots = when (dots) {
                                "." -> ".."
                                ".." -> "..."
                                else -> "."
                            }
                            delay(500)
                        }
                    }
                    Text(
                        text = "Ringing$dots",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    CallTimer(
                        initialDurationSeconds = call.duration().toLong(),
                        modifier = Modifier
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (status == "incoming") {
                // Inbound call actions
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Answer
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFF2ABB86), CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                call?.let { answer(ctx, it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_answer_new),
                            contentDescription = "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Decline
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFFEA4335), CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                call?.let { reject(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_hangup_new),
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            } else {
                // Control Card (Connected/Outgoing)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .shadow(8.dp, RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .shadow(2.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .clip(CircleShape)
                                    .clickable {
                                        val currentMute = BaresipService.isMicMuted
                                        Log.d("CallScreen", "Mute clicked: current=$currentMute -> next=${!currentMute}")
                                        BaresipService.setMicMute(!currentMute)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isMicMuted) R.drawable.ic_mic_off_new else R.drawable.ic_mic_on_new
                                    ),
                                    contentDescription = "Mute",
                                    tint = if (isMicMuted) Color.Red else Color.Unspecified,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Mute",
                                fontSize = 14.sp,
                                color = if (isMicMuted) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Speaker
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .shadow(2.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .clip(CircleShape)
                                    .clickable {
                                        Log.d("CallScreen", "Speaker clicked: current=$isSpeakerOn")
                                        BaresipService.instance?.toggleSpeakerphone()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isSpeakerOn) R.drawable.ic_speaker_on_new else R.drawable.ic_speaker_off_new
                                    ),
                                    contentDescription = "Speaker",
                                    tint = if (isSpeakerOn) Color(0xFF2ABB86) else Color.Unspecified,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Speaker",
                                fontSize = 14.sp,
                                color = if (isSpeakerOn) Color(0xFF2ABB86) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Hang up
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFEA4335), CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            call?.let {
                                Log.d("CallScreen", "Hanging up call ${it.callp}")
                                it.terminated.value = true
                                it.hangup(487, "Request Terminated")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_hangup_new),
                        contentDescription = "Hang up",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
