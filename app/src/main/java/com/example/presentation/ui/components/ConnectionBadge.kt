package com.example.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ConnectionState
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusError

@Composable
fun ConnectionBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (state) {
        ConnectionState.READY, ConnectionState.CONNECTED -> StatusConnected to "متصل مع TaloolaPos"
        ConnectionState.PAIRING_SUCCESS -> StatusConnected to "تم الاقتران بنجاح"
        ConnectionState.CONNECTING,
        ConnectionState.DISCOVERING,
        ConnectionState.SERVER_FOUND,
        ConnectionState.VERIFYING_SERVER,
        ConnectionState.PAIRING_IN_PROGRESS,
        ConnectionState.AUTHENTICATING,
        ConnectionState.RECONNECTING -> StatusConnecting to state.arabicLabel
        ConnectionState.PAIRING_REQUIRED -> StatusConnecting to "الاقتران مطلوب"
        ConnectionState.SESSION_EXPIRED -> StatusError to "جلسة منتهية"
        ConnectionState.ACCESS_DENIED -> StatusError to "صلاحية مرفوضة"
        ConnectionState.SERVER_UNAVAILABLE -> StatusError to "الخادم غير متاح"
        ConnectionState.PROTOCOL_MISMATCH -> StatusError to "عدم تطابق في البروتوكول"
        ConnectionState.PAIRING_EXPIRED -> StatusError to "رمز الاقتران منتهي"
        ConnectionState.PAIRING_INVALID -> StatusError to "بيانات الاقتران غير صالحة"
        ConnectionState.DISCONNECTED -> StatusDisconnected to "غير متصل"
    }

    val animatedColor by animateColorAsState(targetValue = dotColor, label = "dotColor")

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
