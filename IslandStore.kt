package com.loan.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Kind(val priority: Int) { CALL(0), MEDIA(1), TIMER(2), PROGRESS(3), NOTIFICATION(4) }

data class IslandAction(val label: String, val intent: PendingIntent?)

data class IslandActivity(
    val key: String,
    val pkg: String,
    val kind: Kind,
    val title: String,
    val text: String = "",
    val icon: Bitmap? = null,
    val art: Bitmap? = null,
    val progress: Int = -1,
    val playing: Boolean = false,
    val whenMs: Long = 0L,
    val countDown: Boolean = false,
    val actions: List<IslandAction> = emptyList(),
    val contentIntent: PendingIntent? = null,
    val controller: MediaController? = null,
    val seq: Long = System.nanoTime(),
)

/** Estado compartilhado entre o listener de notificações e a UI da ilha. */
object IslandStore {
    private val _list = MutableStateFlow<List<IslandActivity>>(emptyList())
    val list: StateFlow<List<IslandActivity>> = _list

    private val _pulse = MutableStateFlow<IslandActivity?>(null)
    val pulse: StateFlow<IslandActivity?> = _pulse

    /** Deslocamento vertical da ilha (dp) para alinhar com o furinho da câmera. */
    val offsetDp = MutableStateFlow(2)

    fun upsert(a: IslandActivity, alert: Boolean) {
        _list.update { cur -> (cur.filter { it.key != a.key } + a).sortedBy { it.kind.priority } }
        if (alert) _pulse.value = a
        else if (_pulse.value?.key == a.key) _pulse.value = a
    }

    fun showAlert(a: IslandActivity) { _pulse.value = a }

    fun clearPulse(seq: Long) { if (_pulse.value?.seq == seq) _pulse.value = null }

    fun remove(key: String) {
        _list.update { cur -> cur.filter { it.key != key } }
        if (_pulse.value?.key == key) _pulse.value = null
    }
}
