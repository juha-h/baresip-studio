package com.tutpro.baresip

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.ArrowCircleRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InCallContent(
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    isRecording: Boolean,
    isHold: Boolean,
    isHeldByPeer: Boolean,
    isMobileAccount: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleDialpad: () -> Unit,
    onTransfer: () -> Unit,
    onInfo: () -> Unit,
    onHangup: () -> Unit
) {
    val redColor = Color(0xFFEA4335)
    val greenColor = Color(0xFF2ABB86)
    val yellowColor = Color(0xFFF9A825)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Connected In-Call Controls Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 4.dp)
            ) {
                // Row 1: Mute, Keypad, Speaker, Hold/Resume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InCallControlButton(
                        modifier = Modifier.weight(1f),
                        icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMicMuted) stringResource(R.string.unmute) else stringResource(R.string.mute),
                        isActive = isMicMuted,
                        activeColor = redColor,
                        onClick = onToggleMute
                    )

                    InCallControlButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Dialpad,
                        label = stringResource(R.string.keypad),
                        isActive = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        enabled = !isHeldByPeer,
                        onClick = onToggleDialpad
                    )

                    InCallControlButton(
                        modifier = Modifier.weight(1f),
                        icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        label = stringResource(R.string.speaker),
                        isActive = isSpeakerOn,
                        activeColor = greenColor,
                        onClick = onToggleSpeaker
                    )

                    InCallControlButton(
                        modifier = Modifier.weight(1f),
                        icon = if (isHold) Icons.Default.PlayArrow else Icons.Outlined.PauseCircle,
                        label = if (isHold) stringResource(R.string.resume) else stringResource(R.string.hold),
                        isActive = isHold,
                        activeColor = yellowColor,
                        enabled = !isHeldByPeer,
                        onClick = onToggleHold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Record, Transfer, Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isMobileAccount) {
                        InCallControlButton(
                            modifier = Modifier.weight(1f),
                            icon = if (isRecording) Icons.Filled.RadioButtonChecked else Icons.Filled.FiberManualRecord,
                            label = if (isRecording) stringResource(R.string.recording) else stringResource(R.string.record),
                            isActive = isRecording,
                            activeColor = redColor,
                            onClick = onToggleRecord
                        )

                        InCallControlButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.ArrowCircleRight,
                            label = stringResource(R.string.transfer),
                            isActive = false,
                            activeColor = MaterialTheme.colorScheme.primary,
                            enabled = !isHeldByPeer,
                            onClick = onTransfer
                        )
                    }

                    InCallControlButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Info,
                        label = stringResource(R.string.info),
                        isActive = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = onInfo
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // End Call Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(6.dp, CircleShape)
                .background(redColor, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onHangup),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = stringResource(R.string.hangup),
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun InCallDialpadSheet(
    dtmfText: String,
    onSendDtmf: (Char) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // DTMF Header / Display Row (Fixed single-line height, centered, never expands vertically)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val scrollState = rememberScrollState()
            LaunchedEffect(dtmfText) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dtmfText.ifEmpty { stringResource(R.string.keypad) },
                    fontSize = if (dtmfText.isEmpty()) 20.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dtmfText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3x4 DTMF Dialpad Grid
        val dialpadKeys = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (row in dialpadKeys) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for ((digit, subtext) in row) {
                        Surface(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .clickable { onSendDtmf(digit[0]) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = digit,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (subtext.isNotEmpty()) {
                                    Text(
                                        text = subtext,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Dismiss / Hide Keypad Button
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.filledTonalButtonColors(),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.cancel),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InCallControlButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            isActive -> activeColor.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        label = "inCallBtnBg"
    )
    val iconTint by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            isActive -> activeColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "inCallBtnTint"
    )
    val textTint by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            isActive -> activeColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "inCallBtnTextTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            shape = CircleShape,
            color = backgroundColor,
            border = if (isActive && enabled) androidx.compose.foundation.BorderStroke(1.5.dp, activeColor) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive && enabled) FontWeight.Bold else FontWeight.Normal,
            color = textTint
        )
    }
}
