package com.evan.pulse.data.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.evan.pulse.MainActivity
import com.evan.pulse.R
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.ui.l10n.L10n

/**
 * 拨测告警通知。
 *
 * 通道在 Application 启动时就建好，避免用户第一次点通知时通道还没注册。
 * targetSdk 33 起 POST_NOTIFICATIONS 是运行时权限，没授权时静默跳过，不阻塞拨测。
 */
class MonitorNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                L10n.t(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = L10n.t(R.string.monitor_channel_desc)
                setShowBadge(false)
            }
        )
    }

    fun isGranted(): Boolean =
        context.checkSelfPermission(POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 未通的告警，同一目标复用同一个通知 id，反复失败不会刷屏 */
    fun postDown(target: MonitorTarget, message: String) {
        post(target, L10n.t(R.string.monitor_notify_down_title, target.name), message)
    }

    /** 从异常恢复到正常的通知 */
    fun postRecovered(target: MonitorTarget) {
        post(target, L10n.t(R.string.monitor_notify_up_title, target.name), L10n.t(R.string.monitor_notify_up_body))
    }

    private fun post(target: MonitorTarget, title: String, text: String) {
        if (!isGranted()) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TARGET_ID, target.id)
        }
        val pending = PendingIntent.getActivity(
            context,
            (NOTIFY_ID_BASE + (target.id % NOTIFY_ID_SPREAD)).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitor_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pending)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(
                notificationId(target.id),
                notification
            )
        } catch (_: Exception) {
            // 通知投递失败不影响拨测本身
        }
    }

    private fun notificationId(targetId: Long) = NOTIFY_ID_BASE + (targetId % NOTIFY_ID_SPREAD).toInt()

    companion object {
        const val CHANNEL_ID = "pulse_monitor_alert"

        /** 目标 id 随启动意图传递，便于点通知后定位到具体拨测项 */
        const val EXTRA_TARGET_ID = "com.evan.pulse.monitor.target_id"

        private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        private const val NOTIFY_ID_BASE = 20000
        private const val NOTIFY_ID_SPREAD = 1000L
    }
}
