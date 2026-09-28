package com.sengine.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF7867CE), Color(0xFF4A5592))),
                RoundedCornerShape(13.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * .78f, h * .20f)
                lineTo(w * .46f, h * .06f)
                lineTo(w * .18f, h * .24f)
                lineTo(w * .18f, h * .47f)
                lineTo(w * .78f, h * .57f)
                lineTo(w * .78f, h * .78f)
                lineTo(w * .49f, h * .94f)
                lineTo(w * .18f, h * .78f)
            }
            drawPath(path, Color.White, style = Stroke(width = w * .095f))
        }
    }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = StudioColors.muted)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
    }
}

@Composable
fun ToolIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp).then(
            if (highlighted) Modifier.background(StudioColors.raised, RoundedCornerShape(11.dp)) else Modifier,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(21.dp),
            tint = if (enabled) { if (highlighted) StudioColors.violet else StudioColors.muted }
                else StudioColors.muted.copy(alpha = .35f),
        )
    }
}

@Composable
fun Badge(text: String, color: Color = StudioColors.violet) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = .12f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(8.dp)),
    ) {
        Text(
            text = text.uppercase(), color = color, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = .7f.sp,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
