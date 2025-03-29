package com.tutpro.baresip

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat.getString
import com.tutpro.baresip.CustomElements.Text
import com.tutpro.baresip.CustomElements.ThreeButtonAlertDialog

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

            // Draw scrollbar if scrolling or if the animation is still running and lazy column has content
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

    @Composable
    fun ThreeButtonAlertDialog(
        onDismissRequest: () -> Unit,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit,
        onNeutralClick: () -> Unit,
        dialogTitle: String,
        dialogText: String,
    ) {
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        modifier = Modifier.padding(bottom = 16.dp),
                        text = dialogTitle
                    )

                    Text(
                        modifier = Modifier.padding(bottom = 16.dp),
                        text = dialogText
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { onNeutralClick() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Neutral")
                        }
                        TextButton(
                            onClick = { onNegativeClick() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Negative")
                        }
                        Button(
                            onClick = { onPositiveClick() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Positive")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ThreeButtonAlertDialogPreview() {
    var openDialog by remember { mutableStateOf(true) }
    if (openDialog) {
        ThreeButtonAlertDialog(
            onDismissRequest = { openDialog = false },
            onPositiveClick = { openDialog = false },
            onNegativeClick = { openDialog = false },
            onNeutralClick = { openDialog = false },
            dialogTitle = "My Dialog Title",
            dialogText = "This is the message text of my dialog.",
        )
    }
}
