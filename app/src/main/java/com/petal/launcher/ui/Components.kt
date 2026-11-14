@file:OptIn(ExperimentalFoundationApi::class)

package com.petal.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.launcher.data.AppInfo
import com.petal.launcher.data.HomeItem
import com.petal.launcher.data.IconShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Colour used for text/icons that sit directly on the wallpaper. */
val LocalHomeContent = compositionLocalOf { Color.White }

/** Superellipse (n = 4) squircle. */
val SquircleShape: Shape = GenericShape { size, _ ->
    val a = size.width / 2f
    val b = size.height / 2f
    val steps = 80
    for (i in 0..steps) {
        val t = 2.0 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = a + a * (sign(c) * abs(c).pow(0.5)).toFloat()
        val y = b + b * (sign(s) * abs(s).pow(0.5)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

fun IconShape.toShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.ROUNDED_SQUARE -> RoundedCornerShape(24)
    IconShape.TEARDROP -> RoundedCornerShape(50, 50, 12, 50)
}

private val labelShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f)

@Composable
fun AppIconImage(icon: ImageBitmap, shape: IconShape, size: Dp = 52.dp) {
    Image(
        bitmap = icon,
        contentDescription = null,
        modifier = Modifier.size(size).clip(shape.toShape())
    )
}

@Composable
fun CellLabel(text: String, color: Color, shadow: Boolean) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 12.sp, shadow = if (shadow) labelShadow else null),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun AppCell(
    app: AppInfo,
    shape: IconShape,
    showLabel: Boolean,
    contentColor: Color,
    shadow: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(app.icon, shape)
        if (showLabel) CellLabel(app.label, contentColor, shadow)
    }
}

/** Home folder with a 2x2 mini-icon preview. */
@Composable
fun FolderCell(
    item: HomeItem,
    appMap: Map<String, AppInfo>,
    shape: IconShape,
    showLabel: Boolean,
    contentColor: Color,
    shadow: Boolean,
    onClick: () -> Unit
) {
    val previews = item.folderApps.mapNotNull { appMap[it] }.take(4)
    Column(
        Modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(shape.toShape())
                .background(contentColor.copy(alpha = 0.22f)).padding(6.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                for (r in 0..1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (c in 0..1) {
                            val a = previews.getOrNull(r * 2 + c)
                            if (a != null) Image(a.icon, null, Modifier.size(18.dp).clip(CircleShape))
                            else Spacer(Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        if (showLabel) CellLabel(item.folderName.orEmpty().ifBlank { "Folder" }, contentColor, shadow)
    }
}
