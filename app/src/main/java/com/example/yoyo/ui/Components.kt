package com.example.yoyo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoyo.domain.avatarColorFor
import com.example.yoyo.domain.avatarResFor
import com.example.yoyo.domain.initialsFor

const val TABULAR_FIGURES = "tnum"

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    color: Color = Color.White,
    borderColor: Color = BorderColor,
    shape: Shape = RoundedCornerShape(10.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(color, shape)
            .border(1.dp, borderColor, shape)
            .padding(padding),
    ) {
        content()
    }
}

/** Page header (eyebrow, title, description, optional trailing action) above [content]. */
@Composable
fun Screen(
    eyebrow: String,
    title: String,
    description: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    eyebrow.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = Muted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    fontSize = 22.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = TextColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(description, color = Muted, fontSize = 13.sp)
            }
            if (action != null) {
                Box(Modifier.padding(start = 12.dp, bottom = 3.dp)) { action() }
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun Pill(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 22f / 255f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White, RoundedCornerShape(7.dp))
            .padding(12.dp),
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor, maxLines = 1)
    }
}

@Composable
fun CountTile(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
        Text("$count", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun Notice(title: String, message: String, color: Color, icon: ImageVector) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 18f / 255f), shape)
            .border(1.dp, color.copy(alpha = 75f / 255f), shape)
            .padding(13.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(3.dp))
            Text(message, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun EmptyState(title: String, copy: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Muted, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(copy, color = Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun AthleteAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val res = avatarResFor(name)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (res == null) Color(avatarColorFor(name)) else LayerTwo)
            .border(1.dp, BorderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (res == null) {
            Text(
                initialsFor(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.3f).sp,
                maxLines = 1,
            )
        } else {
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.6f),
                modifier = Modifier.size(size),
            )
        }
    }
}

private val ButtonShape = RoundedCornerShape(8.dp)

@Composable
fun PrimaryButton(label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Button(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Blue,
            contentColor = Color.White,
            disabledContainerColor = LayerTwo,
            disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.heightIn(min = 42.dp),
    ) {
        Text(label)
    }
}

@Composable
fun OutlinedAppButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (onClick != null) Muted else BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = modifier.heightIn(min = 42.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
fun SmallButton(label: String, onClick: (() -> Unit)?, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
fun TextAppButton(label: String, onClick: (() -> Unit)?, icon: ImageVector? = null) {
    TextButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        colors = ButtonDefaults.textButtonColors(contentColor = Blue),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
fun DangerButton(label: String, onClick: (() -> Unit)?) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = ButtonShape,
        border = BorderStroke(1.dp, Red.copy(alpha = 130f / 255f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.heightIn(min = 42.dp),
    ) {
        Text(label)
    }
}

@Composable
fun ToolButton(label: String, onClick: (() -> Unit)?) {
    TextButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        colors = ButtonDefaults.textButtonColors(contentColor = Blue),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.heightIn(min = 38.dp),
    ) {
        Text(label, fontSize = 15.sp)
    }
}

/** Lays [items] out in rows of [columns] equal-width cells, like a fixed-column Wrap. */
@Composable
fun <T> GridRows(
    items: List<T>,
    columns: Int,
    spacing: Dp,
    key: ((T) -> Any)? = null,
    cell: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        if (key != null) {
                            androidx.compose.runtime.key(key(item)) { cell(item) }
                        } else {
                            cell(item)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
