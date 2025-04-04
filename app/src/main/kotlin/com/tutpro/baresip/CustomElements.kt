package com.tutpro.baresip

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat.getString

object CustomElements {

    @Composable
    fun Text(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = LocalCustomColors.current.itemText,
        fontSize: TextUnit = 16.sp,
        fontWeight: FontWeight = FontWeight.Normal,
        textAlign: TextAlign? = null,
        maxLines: Int = Int.MAX_VALUE,
    ) {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PullToRefreshBox(
        isRefreshing: Boolean,
        onRefresh: () -> Unit,
        modifier: Modifier = Modifier,
        contentAlignment: Alignment = Alignment.TopStart,
        enabled: Boolean = true,
        content: @Composable BoxScope.() -> Unit,
    ) {
        val refreshState = rememberPullToRefreshState()

        Box(
            modifier.pullToRefresh(
                state = refreshState,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                enabled = enabled,
            ),
            contentAlignment = contentAlignment,
        ) {
            content()
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = refreshState,
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
            containerColor = LocalCustomColors.current.grayLight,
            modifier = Modifier.background(
                LocalCustomColors.current.popupBackground.copy(alpha = 0.95f)
            )
        ) {
            val itemsIterator = items.iterator()
            while (itemsIterator.hasNext()) {
                val item = itemsIterator.next()
                DropdownMenuItem(
                    text = { Text(item, color = LocalCustomColors.current.light) },
                    onClick = { onItemClick(item) }
                )
                if (itemsIterator.hasNext())
                    HorizontalDivider(color = LocalCustomColors.current.spinnerDivider)
            }
        }
    }

    @Composable
    fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                onCheckedChange(!checked)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = LocalCustomColors.current.accent,
                uncheckedColor = LocalCustomColors.current.strong,
                checkmarkColor = LocalCustomColors.current.strong,
            )
        )
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
    fun DrawDrawable(
        drawableId: Int,
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
        tint: Color? = null
    ) {
        val image = painterResource(id = drawableId)
        Image(
            painter = image,
            contentDescription = contentDescription,
            modifier = modifier,
            colorFilter = if (tint != null) ColorFilter.tint(tint) else null
        )
    }

    @Composable
    fun Modifier.verticalScrollbar(
        state: LazyListState,
        width: Dp = 8.dp,
        alwaysShow: Boolean = true,
        color: Color = LocalCustomColors.current.gray
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
                drawRect(
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
        BasicAlertDialog(
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            onDismissRequest = {
                keyboardController?.hide()
                showPasswordDialog.value = false
            }
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                color = LocalCustomColors.current.background,
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                        color = LocalCustomColors.current.alert,
                    )
                    if (message != "")
                        Text(
                            text = message,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp),
                            color = LocalCustomColors.current.itemText,
                        )
                    OutlinedTextField(
                        value = password.value,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = LocalCustomColors.current.textFieldBackground,
                            unfocusedContainerColor = LocalCustomColors.current.textFieldBackground,
                            cursorColor = LocalCustomColors.current.primary,
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
                                showPasswordDialog.value = true
                            }) {
                                Icon(
                                    if (showPassword.value)
                                        ImageVector.vectorResource(R.drawable.visibility)
                                    else
                                        ImageVector.vectorResource(R.drawable.visibility_off),
                                    contentDescription = "Visibility",
                                    tint = LocalCustomColors.current.grayDark

                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            color = LocalCustomColors.current.dark
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    LaunchedEffect(key1 = Unit) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    Row(
                        horizontalArrangement = Arrangement.Start
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
                                color = LocalCustomColors.current.gray
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                keyboardController?.hide()
                                showPasswordDialog.value = false
                                password.value = password.value.trim()
                                if (!Account.checkAuthPass(password.value)) {
                                    Toast.makeText(
                                        ctx,
                                        String.format(
                                            getString(
                                                ctx,
                                                R.string.invalid_authentication_password
                                            ), password.value
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
                                color = LocalCustomColors.current.alert
                            )
                        }
                    }
                }
            }
        }
    }
}
