package com.tutpro.baresip

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

object CustomElements {

    @Composable
    fun Button(
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        shape: Shape,
        border: BorderStroke? = null,
        color: Color,
        content: @Composable RowScope.() -> Unit
    ) {
        Surface(
            shape = shape,
            color = color,
            border = border,
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() },
                    )
                }
                .then(modifier),
        ) {
            Row(
                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }

    @Composable
    fun DropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        items: List<String>,
        onItemClick: (String) -> Unit
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            val itemsIterator = items.iterator()
            while (itemsIterator.hasNext()) {
                val item = itemsIterator.next()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ) },
                    onClick = { onItemClick(item) }
                )
                if (itemsIterator.hasNext())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    @Composable
    fun TextAvatar(name: String, color: Int) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(SolidColor(Color(color)))
            }
            val text = if (name == "") "" else name[0].toString()
            Text(text, color = Color.White, fontSize = 20.sp)
        }
    }

    @Composable
    fun ImageAvatar(bitmap: Bitmap) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        )
    }

    @Composable
    fun Modifier.verticalScrollbar(
        state: ScrollState,
        scrollbarWidth: Dp = 4.dp,
        alwaysShow: Boolean = true,
        color: Color = MaterialTheme.colorScheme.outlineVariant
    ): Modifier {
        val alpha by animateFloatAsState(
            targetValue = if(state.isScrollInProgress || alwaysShow) 1f else 0f,
            animationSpec = tween(400, delayMillis = if(state.isScrollInProgress) 0 else 700)
        )
        return this then Modifier.drawWithContent {
            drawContent()
            val viewHeight = state.viewportSize.toFloat()
            val contentHeight = state.maxValue + viewHeight
            val scrollbarHeight = (viewHeight * (viewHeight / contentHeight )).coerceIn(10.dp.toPx() .. viewHeight)
            val variableZone = viewHeight - scrollbarHeight
            val scrollbarOffsetY = (state.value.toFloat() / state.maxValue) * variableZone
            drawRoundRect(
                cornerRadius = CornerRadius(scrollbarWidth.toPx() / 2, scrollbarWidth.toPx() / 2),
                color = color,
                topLeft = Offset(this.size.width - scrollbarWidth.toPx(), scrollbarOffsetY),
                size = Size(scrollbarWidth.toPx(), scrollbarHeight),
                alpha = alpha
            )
        }
    }

    @Composable
    fun Modifier.verticalScrollbar(
        state: LazyListState,
        width: Dp = 8.dp,
        alwaysShow: Boolean = true,
        color: Color = MaterialTheme.colorScheme.outlineVariant
    ): Modifier {
        val targetAlpha = if (state.isScrollInProgress || alwaysShow) 1f else 0f
        val duration = if (state.isScrollInProgress) 150 else 500
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = duration)
        )
        return drawWithContent {
            drawContent()
            val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
            val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f
            if (needDrawScrollbar && firstVisibleElementIndex != null) {
                val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
                val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
                val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight
                drawRoundRect(
                    cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2),
                    color = color,
                    topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                    size = Size(width.toPx(), scrollbarHeight),
                    alpha = alpha
                )
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AlertDialog(
        showDialog: MutableState<Boolean>,
        title: String,
        message: String,
        positiveButtonText: String = "",
        onPositiveClicked: () -> Unit = {},
        negativeButtonText: String = "",
        onNegativeClicked: () -> Unit = {},
        neutralButtonText: String = "",
        onNeutralClicked: () -> Unit = {}
    ) {
        if (showDialog.value) {
            BasicAlertDialog(
                onDismissRequest = { showDialog.value = false },
                content = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = title,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = message,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // New logic starts here
                            if (positiveButtonText.isNotEmpty()) {
                                val buttonCount = listOf(positiveButtonText, negativeButtonText, neutralButtonText)
                                    .count { it.isNotEmpty() }

                                if (buttonCount >= 3) {
                                    // Use a Column for 3 buttons, aligned to the end (right)
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        // The positive button is usually last for emphasis
                                        if (negativeButtonText.isNotEmpty()) {
                                            TextButton(onClick = {
                                                onNegativeClicked()
                                                showDialog.value = false
                                            }) {
                                                Text(
                                                    text = negativeButtonText.uppercase(),
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (neutralButtonText.isNotEmpty()) {
                                            TextButton(onClick = {
                                                onNeutralClicked()
                                                showDialog.value = false
                                            }) {
                                                Text(
                                                    text = neutralButtonText.uppercase(),
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        TextButton(onClick = {
                                            onPositiveClicked()
                                            showDialog.value = false
                                        }) {
                                            Text(
                                                text = positiveButtonText.uppercase(),
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                } else {
                                    // Use the existing Row for 1 or 2 buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (negativeButtonText.isNotEmpty()) {
                                            TextButton(onClick = {
                                                onNegativeClicked()
                                                showDialog.value = false
                                            }) {
                                                Text(
                                                    text = negativeButtonText.uppercase(),
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (neutralButtonText.isNotEmpty()) {
                                            TextButton(onClick = {
                                                onNeutralClicked()
                                                showDialog.value = false
                                            }) {
                                                Text(
                                                    text = neutralButtonText.uppercase(),
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        TextButton(onClick = {
                                            onPositiveClicked()
                                            showDialog.value = false
                                        }) {
                                            Text(
                                                text = positiveButtonText.uppercase(),
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SelectableAlertDialog(
        openDialog: MutableState<Boolean>,
        title: String,
        items: List<String>,
        onItemClicked: (Int) -> Unit,
        neutralButtonText: String = "",
        onNeutralClicked: () -> Unit = {}
    ) {
        if (openDialog.value) {
            BasicAlertDialog(
                onDismissRequest = {
                    openDialog.value = false
                },
                content = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = title,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                itemsIndexed(items) { index, item ->
                                    TextButton(
                                        onClick = {
                                            onItemClicked(index)
                                            openDialog.value = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = item,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                            }
                            if (neutralButtonText.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            onNeutralClicked()
                                            openDialog.value = false
                                        }
                                    ) {
                                        Text(
                                            text = neutralButtonText.uppercase(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PasswordDialog(
        ctx: Context,
        showPasswordDialog: MutableState<Boolean>,
        password: MutableState<String>,
        keyboardController: SoftwareKeyboardController?,
        title: String,
        message: String = "",
        okAction: () -> Unit,
        cancelAction: () -> Unit
    ) {
        val showPassword = remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        if (showPasswordDialog.value) {
            BasicAlertDialog(
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
                onDismissRequest = {
                    keyboardController?.hide()
                    showPasswordDialog.value = false
                }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (message.isNotEmpty())
                            Text(
                                text = message,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        OutlinedTextField(
                            value = password.value,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                            onValueChange = {
                                password.value = it
                            },
                            visualTransformation = if (showPassword.value)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    showPassword.value = !showPassword.value
                                }) {
                                    Icon(
                                        imageVector = if (showPassword.value)
                                            Icons.Filled.Visibility
                                        else
                                            Icons.Filled.VisibilityOff,
                                        contentDescription = "Visibility",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                        LaunchedEffect(key1 = Unit) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    keyboardController?.hide()
                                    showPasswordDialog.value = false
                                    cancelAction()
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    keyboardController?.hide()
                                    showPasswordDialog.value = false
                                    password.value = password.value.trim()
                                    if (!Account.checkAuthPass(password.value)) {
                                        Toast.makeText(
                                            ctx,
                                            String.format(
                                                ctx.getString(R.string.invalid_authentication_password),
                                                password.value
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        password.value = ""
                                    }
                                    okAction()
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.ok),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
