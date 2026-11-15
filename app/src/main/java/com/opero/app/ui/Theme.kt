package com.opero.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0D0F13)
    val Rail = Color(0xFF111419)
    val Surface = Color(0xFF15181D)
    val Surface2 = Color(0xFF1A1D23)
    val Line = Color(0xFF2E343E)
    val Blue = Color(0xFF5B9DFF)
    val OnBlue = Color(0xFF06142B)
    val BlueSoft = Color(0xFF1C2C47)
    val BlueText = Color(0xFF9CC3FF)
    val Amber = Color(0xFFF5B544)
    val AmberSoft = Color(0xFF3A2E14)
    val AmberText = Color(0xFFF5C46A)
    val Text = Color(0xFFF2F4F7)
    val Sub = Color(0xFFA3AAB6)
    val Body = Color(0xFFC2C8D2)
    val Rec = Color(0xFFFF8A65)
}

@Composable
fun OperoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Blue,
            onPrimary = Palette.OnBlue,
            background = Palette.Bg,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            onBackground = Palette.Text,
        ),
        content = content,
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = Palette.Surface, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Chip(text: String, bg: Color = Palette.BlueSoft, fg: Color = Palette.BlueText, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = fg,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun Label(text: String) {
    Text(text, color = Palette.Sub, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}
