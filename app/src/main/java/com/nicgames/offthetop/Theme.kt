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
import androidx.compose.ui.graphics.vector.ImageVector
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
// Approved card-table palette: flat green, cream stock, green correct and red pass.
data class PressColors(val paper: Color, val card: Color, val ink: Color, val muted: Color,
    val accent: Color, val onAccent: Color, val wash: Color,
    val edge: Color, val shadow: Color,
    val cardFace: Color, val cardInk: Color, val cardMuted: Color,
    val correct: Color, val pass: Color, val onOutcome: Color)
val Day = PressColors(Color(0xFF113E2D), Color(0xFF193F30), Color(0xFFF6EFDF), Color(0xFFCDDDC8),
    Color(0xFFB9E0BD), Color(0xFF113E2D), Color(0xFF264A36),
    Color(0xFF789C7F), Color(0xFF06251A),
    Color(0xFFF6EFDF), Color(0xFF17392C), Color(0xFF536451),
    Color(0xFF236C42), Color(0xFFAC3037), Color(0xFFF6EFDF))
val Night = PressColors(Color(0xFF0A281D), Color(0xFF123526), Color(0xFFE9E3D5), Color(0xFFBACDB7),
    Color(0xFFB0D8B4), Color(0xFF0A281D), Color(0xFF22412F),
    Color(0xFF688C71), Color(0xFF04190F),
    Color(0xFFE5DDCD), Color(0xFF17392C), Color(0xFF4C5B47),
    Color(0xFF1D5E38), Color(0xFF99282F), Color(0xFFF6EFDF))
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
            outline = p.edge, outlineVariant = p.muted,
            secondary = p.accent, onSecondary = p.onAccent, secondaryContainer = p.wash, onSecondaryContainer = p.ink,
            tertiary = p.accent, onTertiary = p.onAccent, tertiaryContainer = p.wash, onTertiaryContainer = p.ink,
            error = p.ink, onError = p.paper, errorContainer = p.wash, onErrorContainer = p.ink),
            typography = Typography(bodyLarge = TextStyle(fontFamily = Body, fontSize = 17.sp), bodyMedium = TextStyle(fontFamily = Body, fontSize = 15.sp),
                titleLarge = TextStyle(fontFamily = Headline, fontSize = 26.sp), labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp)), content = content)
    }
}

/** Flat opaque surfaces in both themes. No gradients, glows, or decorative effects. */
@Composable internal fun PageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(LocalPress.current.paper), content = content)
}

@Composable fun Rule(modifier: Modifier = Modifier) {
    val p = LocalPress.current
    Box(modifier.fillMaxWidth().height(1.dp).background(p.edge.copy(alpha = 0.5f)))
}

@Composable fun PressButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null, pass: Boolean = false) {
    val p = LocalPress.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val bg = when { pass -> p.pass; primary -> p.correct; else -> p.card }
    Box(modifier.padding(end = 4.dp, bottom = 4.dp).offset(if (pressed) 3.dp else 0.dp, if (pressed) 3.dp else 0.dp)
        .drawBehind { if (!pressed) drawRect(p.shadow, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size) }
        .background(if (enabled) bg else p.card, RoundedCornerShape(2.dp)).border(2.dp, p.edge, RoundedCornerShape(2.dp))
        .clickable(enabled = enabled, interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        val foreground = if (!enabled) p.muted else if (primary || pass) p.onOutcome else p.ink
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp))
            Text(text, color = foreground, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}