package com.opero.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, height: Int = 60, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Blue, contentColor = Palette.OnBlue),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, height: Int = 60, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height.dp),
        border = BorderStroke(1.dp, Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
    }
}
