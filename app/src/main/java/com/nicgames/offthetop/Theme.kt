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
data class PressColors(val paper: Color, val card: Color, val ink: Color, val muted: Color, val red: Color, val green: Color, val purple: Color)
val Day = PressColors(Color(0xFFEFE9DC), Color(0xFFFBF8F1), Color(0xFF17150F), Color(0xFF6E6656), Color(0xFFC62D26), Color(0xFF157F41), Color(0xFF6C2E9C))
val Night = PressColors(Color(0xFF14130F), Color(0xFF1F1D16), Color(0xFFF1EBDC), Color(0xFFB5AB97), Color(0xFFF2685C), Color(0xFF45C077), Color(0xFFB183E8))
val LocalPress = staticCompositionLocalOf { Day }

@Composable fun PressTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "Night" || mode == "System" && isSystemInDarkTheme()
    val p = if (dark) Night else Day
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPress provides p) {
        MaterialTheme(colorScheme = scheme.copy(primary = p.ink, onPrimary = p.paper, background = p.paper, onBackground = p.ink,
            surface = p.card, onSurface = p.ink, onSurfaceVariant = p.muted, outline = p.ink, secondary = p.purple),
            typography = Typography(bodyLarge = TextStyle(fontFamily = Body, fontSize = 17.sp), bodyMedium = TextStyle(fontFamily = Body, fontSize = 15.sp),
                titleLarge = TextStyle(fontFamily = Headline, fontSize = 26.sp), labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp)), content = content)
    }
}

@Composable fun Rule(modifier: Modifier = Modifier, colored: Boolean = false) {
    val p = LocalPress.current
    if (colored) Row(modifier.fillMaxWidth().height(5.dp)) {
        listOf(p.red, p.green, p.purple).forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
    } else Box(modifier.fillMaxWidth().height(1.dp).background(p.ink.copy(alpha = 0.35f)))
}

@Composable fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = LocalPress.current.muted) {
    Text(text, modifier, color = color, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp)
}

@Composable fun PressButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, accent: Color? = null, enabled: Boolean = true) {
    val p = LocalPress.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val bg = accent ?: if (primary) p.ink else p.card
    Box(modifier.padding(end = 4.dp, bottom = 4.dp).offset(if (pressed) 3.dp else 0.dp, if (pressed) 3.dp else 0.dp)
        .drawBehind { if (!pressed) drawRect(p.ink, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size) }
        .background(if (enabled) bg else p.card, RoundedCornerShape(2.dp)).border(2.dp, p.ink, RoundedCornerShape(2.dp))
        .clickable(enabled = enabled, interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (!enabled) p.muted else if (accent != null) Color.White else if (primary) p.paper else p.ink,
            fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}