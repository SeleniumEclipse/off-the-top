package com.nicgames.offthetop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Headline = FontFamily(Font(R.font.archivo_black, FontWeight.Black))
val Body = FontFamily(Font(R.font.barlow_regular), Font(R.font.barlow_medium, FontWeight.Medium), Font(R.font.barlow_semibold, FontWeight.SemiBold), Font(R.font.barlow_bold, FontWeight.Bold))
// One accent ink: teal. Navy, ivory and their shades do the rest.
data class PressColors(val paper: Color, val card: Color, val ink: Color, val muted: Color,
    val accent: Color, val onAccent: Color, val wash: Color)
val Day = PressColors(Color(0xFFF4EFE5), Color(0xFFFFFCF6), Color(0xFF192D38), Color(0xFF576974),
    Color(0xFF006C67), Color(0xFFFFFCF6), Color(0xFFD9EAE5))
val Night = PressColors(Color(0xFF12212B), Color(0xFF1A303B), Color(0xFFF4EFE5), Color(0xFFB1C2C5),
    Color(0xFF64CEC1), Color(0xFF12212B), Color(0xFF243E46))
val LocalPress = staticCompositionLocalOf { Day }

@Composable fun PressTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "Night" || mode == "System" && isSystemInDarkTheme()
    val p = if (dark) Night else Day
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPress provides p) {
        MaterialTheme(colorScheme = scheme.copy(primary = p.accent, onPrimary = p.onAccent,
            primaryContainer = p.wash, onPrimaryContainer = p.ink,
            background = p.paper, onBackground = p.ink, surface = p.card, onSurface = p.ink,
            surfaceVariant = p.wash, onSurfaceVariant = p.muted, surfaceTint = p.accent,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.wash,
            outline = p.ink, outlineVariant = p.muted,
            secondary = p.accent, onSecondary = p.onAccent, secondaryContainer = p.wash, onSecondaryContainer = p.ink,
            tertiary = p.accent, onTertiary = p.onAccent, tertiaryContainer = p.wash, onTertiaryContainer = p.ink,
            error = p.ink, onError = p.paper, errorContainer = p.wash, onErrorContainer = p.ink),
            typography = Typography(bodyLarge = TextStyle(fontFamily = Body, fontSize = 17.sp), bodyMedium = TextStyle(fontFamily = Body, fontSize = 15.sp),
                titleLarge = TextStyle(fontFamily = Headline, fontSize = 26.sp), labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp)), content = content)
    }
}

@Composable fun Rule(modifier: Modifier = Modifier, colored: Boolean = false) {
    val p = LocalPress.current
    if (colored) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(p.accent))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink.copy(alpha = .25f)))
    } else Box(modifier.fillMaxWidth().height(1.dp).background(p.ink.copy(alpha = 0.35f)))
}

@Composable fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = LocalPress.current.muted) {
    Text(text, modifier, color = color, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp)
}

@Composable fun PressButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val p = LocalPress.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val bg = if (primary) p.accent else p.card
    Box(modifier.padding(end = 4.dp, bottom = 4.dp).offset(if (pressed) 3.dp else 0.dp, if (pressed) 3.dp else 0.dp)
        .drawBehind { if (!pressed) drawRect(p.ink, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size) }
        .background(if (enabled) bg else p.card, RoundedCornerShape(2.dp)).border(2.dp, p.ink, RoundedCornerShape(2.dp))
        .clickable(enabled = enabled, interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (!enabled) p.muted else if (primary) p.onAccent else p.ink,
            fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}