package com.loan.island

import android.app.Notification
import android.app.NotificationManager
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Lê as notificações do sistema e transforma em "atividades" da ilha:
 * mídia, chamadas, cronômetros/timers, downloads/progresso e notificações comuns (alerta expandido).
 * Também hospeda a janela de sobreposição enquanto o acesso a notificações estiver ativo.
 */
class IslandListener : NotificationListenerService() {

    private var host: OverlayHost? = null

    override fun onListenerConnected() {
        host?.hide()
        host = OverlayHost(this).also { it.show() }
        activeNotifications?.forEach { runCatching { handle(it, true) } }
    }

    override fun onListenerDisconnected() {
        host?.hide(); host = null
    }

    override fun onDestroy() {
        host?.hide(); host = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { handle(sbn, false) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IslandStore.remove(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX)
        val prog = ex.getInt(Notification.EXTRA_PROGRESS)
        val indet = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val token = mediaToken(ex)

        val kind = when {
            token != null -> Kind.MEDIA
            n.category == Notification.CATEGORY_CALL -> Kind.CALL
            ongoing && ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) -> Kind.TIMER
            max > 0 && !indet -> Kind.PROGRESS
            else -> Kind.NOTIFICATION
        }
        if (kind == Kind.NOTIFICATION && title.isEmpty() && text.isEmpty()) return

        val appIcon = runCatching { packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
        val icon = n.getLargeIcon()?.toBmp() ?: appIcon
        val actions = (n.actions ?: emptyArray())
            .filter { it.remoteInputs.isNullOrEmpty() }
            .take(3)
            .map { IslandAction(it.title?.toString().orEmpty(), it.actionIntent) }

        val key = sbn.key
        val existing = IslandStore.list.value.firstOrNull { it.key == key }

        when (kind) {
            Kind.NOTIFICATION -> {
                IslandStore.remove(key)
                if (initial || ongoing) return
                val r = Ranking()
                if (!currentRanking.getRanking(key, r) || r.importance < NotificationManager.IMPORTANCE_DEFAULT) return
                IslandStore.showAlert(
                    IslandActivity(
                        key = key, pkg = pkg, kind = kind,
                        title = title.ifEmpty { appLabel(pkg) }, text = text,
                        icon = icon, actions = actions, contentIntent = n.contentIntent,
                    )
                )
            }

            Kind.MEDIA -> {
                val c = MediaController(this, token!!)
                val md = c.metadata
                val mt = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: title
                val ma = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: text
                val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: icon
                val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
                IslandStore.upsert(
                    IslandActivity(
                        key = key, pkg = pkg, kind = kind, title = mt, text = ma,
                        icon = appIcon, art = art, playing = playing,
                        controller = c, contentIntent = n.contentIntent,
                    ),
                    alert = !initial && existing?.title != mt,
                )
            }

            Kind.CALL -> IslandStore.upsert(
                IslandActivity(
                    key = key, pkg = pkg, kind = kind, title = title, text = text,
                    icon = icon, actions = actions, contentIntent = n.contentIntent,
                ),
                alert = existing == null && !initial,
            )

            Kind.TIMER -> IslandStore.upsert(
                IslandActivity(
                    key = key, pkg = pkg, kind = kind, title = title, text = text, icon = icon,
                    whenMs = n.`when`,
                    countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
                    actions = actions, contentIntent = n.contentIntent,
                ),
                alert = false,
            )

            Kind.PROGRESS -> IslandStore.upsert(
                IslandActivity(
                    key = key, pkg = pkg, kind = kind, title = title.ifEmpty { appLabel(pkg) }, text = text,
                    icon = icon, progress = (prog * 100 / max).coerceIn(0, 100),
                    actions = actions, contentIntent = n.contentIntent,
                ),
                alert = false,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun mediaToken(ex: Bundle): MediaSession.Token? =
        ex.getParcelable(Notification.EXTRA_MEDIA_SESSION)

    private fun Icon.toBmp(): Bitmap? =
        runCatching { loadDrawable(this@IslandListener)?.toBitmap(96, 96) }.getOrNull()

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
