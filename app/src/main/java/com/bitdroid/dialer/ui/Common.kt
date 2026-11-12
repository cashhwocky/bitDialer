package com.bitdroid.dialer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bitdroid.dialer.data.Prefs

/** bitDroid's toggle-switch mark. */
@Composable
fun ToggleMark() {
    Box(Modifier.size(38.dp, 22.dp).clip(CircleShape).background(Bit.Accent)) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(16.dp).clip(CircleShape).background(Bit.Black))
    }
}

@Composable
fun SectionRule(label: String) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = mono(11.5f, spacing = 1.6f))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

/** Faint Material You (Monet) light behind the black. */
@Composable
fun Modifier.monetGlow(on: Boolean, strong: Boolean = false): Modifier {
    if (!on) return this
    val dyn = dynamicDarkColorScheme(LocalContext.current)
    val a = dyn.primary.copy(alpha = if (strong) 0.30f else 0.17f)
    val b = dyn.tertiary.copy(alpha = if (strong) 0.20f else 0.11f)
    return this.drawBehind {
        drawRect(Brush.radialGradient(listOf(a, Color.Transparent), center = Offset(size.width * 0.15f, 0f), radius = size.width * 1.2f))
        drawRect(Brush.radialGradient(listOf(b, Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 1.15f))
    }
}

/** Glass cards: each row its own card. Frosted panel: rows share one rounded panel. */
fun rowSurface(style: Prefs.ListStyle, first: Boolean, last: Boolean): Modifier = when (style) {
    Prefs.ListStyle.CARDS -> Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(Color.White.copy(alpha = 0.045f))
        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
    Prefs.ListStyle.PANEL -> {
        val r = 28.dp
        val shape = RoundedCornerShape(
            topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
            bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
        )
        Modifier.padding(horizontal = 10.dp).clip(shape).background(Color.White.copy(alpha = 0.04f))
            .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)
    }
}
