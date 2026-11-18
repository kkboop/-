package com.loan.island

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Janela de sobreposição (TYPE_APPLICATION_OVERLAY) que hospeda a ilha em Compose. Uma instância = um ciclo de vida. */
class OverlayHost(private val ctx: Context) : LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    init { controller.performRestore(null) }

    fun show() {
        if (view != null || !Settings.canDrawOverlays(ctx)) return
        registry.currentState = Lifecycle.State.RESUMED

        val density = ctx.resources.displayMetrics.density
        IslandStore.offsetDp.value = ctx.getSharedPreferences("island", Context.MODE_PRIVATE).getInt("offset", 2)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            y = (IslandStore.offsetDp.value * density).toInt()
        }
        params = lp

        val v = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setContent { IslandRoot() }
        }
        wm.addView(v, lp)
        view = v

        scope.launch {
            IslandStore.offsetDp.collect { dp ->
                val p = params ?: return@collect
                val vv = view ?: return@collect
                p.y = (dp * density).toInt()
                runCatching { wm.updateViewLayout(vv, p) }
            }
        }
    }

    fun hide() {
        scope.cancel()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
