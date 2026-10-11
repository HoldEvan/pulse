package com.evan.pulse.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.model.WsConnectionState
import top.yukonga.miuix.kmp.basic.Text

/** 各协议共用连接状态色：灰=空闲/关闭，蓝=连接中，绿=已连接，红=失败 */
fun connectionStateColor(state: SseConnectionState): Color = when (state) {
    SseConnectionState.IDLE -> Color.Gray
    SseConnectionState.CONNECTING -> Color(0xFF1565C0)
    SseConnectionState.CONNECTED -> Color(0xFF2E7D32)
    SseConnectionState.CLOSED -> Color.Gray
    SseConnectionState.FAILED -> Color(0xFFC62828)
}

fun connectionStateColor(state: WsConnectionState): Color = when (state) {
    WsConnectionState.IDLE -> Color.Gray
    WsConnectionState.CONNECTING -> Color(0xFF1565C0)
    WsConnectionState.CONNECTED -> Color(0xFF2E7D32)
    WsConnectionState.CLOSED -> Color.Gray
    WsConnectionState.FAILED -> Color(0xFFC62828)
}

fun connectionStateColor(state: MqttConnectionState): Color = when (state) {
    MqttConnectionState.IDLE -> Color.Gray
    MqttConnectionState.CONNECTING -> Color(0xFF1565C0)
    MqttConnectionState.CONNECTED -> Color(0xFF2E7D32)
    MqttConnectionState.DISCONNECTED -> Color.Gray
    MqttConnectionState.FAILED -> Color(0xFFC62828)
}

@Composable
fun ConnectionStateIndicator(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
