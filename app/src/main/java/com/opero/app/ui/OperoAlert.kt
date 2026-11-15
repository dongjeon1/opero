package com.opero.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 되돌릴 수 없는 동작에 쓰는 빨간 계열 확인 버튼 색 */
private val Danger = Color(0xFFFF7A6B)

/**
 * 앱 공통 알림 창.
 * - 확인이 필요한 동작: confirmText + dismissText("취소")
 * - 결과 안내: confirmText("확인")만
 */
@Composable
fun OperoAlert(
    title: String,
    message: String,
    confirmText: String = "확인",
    dismissText: String? = null,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = onConfirm,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) },
        text = { Text(message, color = Palette.Body, fontSize = 17.sp, lineHeight = 24.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) Danger else Palette.Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it, color = Palette.Sub, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}
