package com.lscardinali.trill.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lscardinali.trill.R

/** Cocoon's dark surfaces with Trill's violet pod accent (matches the launcher icon). */
object CC {
    val Bg = Color(0xFF1E1E1E)
    val BgTop = Color(0xFF2C2C2C)
    val Card = Color(0xFF2C2C2C)
    val CardHi = Color(0xFF363636)
    val Divider = Color(0xFF3A3A3A)
    val Glyph = Color(0xFF4E4E4E)
    val Text = Color(0xFFF3F3F3)
    val Sub = Color(0xFFBDBDBD)
    val Dim = Color(0xFF808080)
    // Gradient stops, same as the icon background (light lilac → violet).
    val Mint = Color(0xFFC9A8FF)
    val Green = Color(0xFF8A62EE)
    val Accent = Color(0xFFB08CFF)
    val OnAccent = Color(0xFF1A1033)
    val Danger = Color(0xFFE8737A)

    val AccentBrush = Brush.linearGradient(listOf(Mint, Green))
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun Typography.withFont(f: FontFamily): Typography {
    fun TextStyle.p() = copy(fontFamily = f)
    return copy(
        displayLarge = displayLarge.p(), displayMedium = displayMedium.p(), displaySmall = displaySmall.p(),
        headlineLarge = headlineLarge.p(), headlineMedium = headlineMedium.p(), headlineSmall = headlineSmall.p(),
        titleLarge = titleLarge.p(), titleMedium = titleMedium.p(), titleSmall = titleSmall.p(),
        bodyLarge = bodyLarge.p(), bodyMedium = bodyMedium.p(), bodySmall = bodySmall.p(),
        labelLarge = labelLarge.p(), labelMedium = labelMedium.p(), labelSmall = labelSmall.p(),
    )
}

@Composable
fun TrillTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CC.Accent,
            onPrimary = CC.OnAccent,
            background = CC.Bg,
            surface = CC.Card,
            onBackground = CC.Text,
            onSurface = CC.Text,
        ),
        typography = Typography().withFont(Poppins),
        content = content,
    )
}
