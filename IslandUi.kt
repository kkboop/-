package com.loan.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

sealed interface Ui {
    data object Idle : Ui
    data class Compact(val main: IslandActivity, val second: IslandActivity?) : Ui
    data class Expanded(val act: IslandActivity) : Ui
}

private val Green = Color(0xFF30D158)
private val Red = Color(0xFFFF453A)
private val Blue = Color(0xFF0A84FF)
private val Orange = Color(0xFFFF9F0A)
private val Gray = Color(0xFF2C2C2E)

@Composable
fun IslandRoot() {
    val list by IslandStore.list.collectAsState()
    val pulse by IslandStore.pulse.collectAsState()
    var manualKey by remember { mutableStateOf<String?>(null) }

    // Alertas expandem sozinhos e recolhem depois de alguns segundos.
    LaunchedEffect(pulse?.seq) {
        val p = pulse
        if (p != null) {
            delay(5500)
            IslandStore.clearPulse(p.seq)
        }
    }
    // Expansão manual (toque longo) também recolhe sozinha.
    LaunchedEffect(manualKey) {
        if (manualKey != null) {
            delay(7000)
            manualKey = null
        }
    }

    val p = pulse
    val manual = list.firstOrNull { it.key == manualKey }
    val ui: Ui = when {
        p != null -> Ui.Expanded(p)
        manual != null -> Ui.Expanded(manual)
        list.isNotEmpty() -> Ui.Compact(list[0], list.getOrNull(1))
        else -> Ui.Idle
    }

    val secondary = (ui as? Ui.Compact)?.second
    val shownSecond = remember { mutableStateOf<IslandActivity?>(null) }
    if (secondary != null) shownSecond.value = secondary

    val targetW = when (ui) {
        Ui.Idle -> 0.dp
        is Ui.Compact -> 168.dp
        is Ui.Expanded -> 356.dp
    }
    val w by animateDpAsState(targetW, spring(dampingRatio = 0.68f, stiffness = 420f), label = "w")
    val r by animateDpAsState(
        if (ui is Ui.Expanded) 34.dp else 18.dp,
        spring(dampingRatio = 0.8f, stiffness = 500f), label = "r",
    )
    // Espaço invisível à esquerda para manter a pílula principal centralizada no furinho quando há 2 atividades.
    val balance by animateDpAsState(
        if (secondary != null) 40.dp else 0.dp,
        spring(dampingRatio = 0.8f, stiffness = 500f), label = "bal",
    )

    Row(verticalAlignment = Alignment.Top) {
        Spacer(Modifier.width(balance))
        Box(
            Modifier
                .width(w)
                .animateContentSize(spring(dampingRatio = 0.75f, stiffness = 420f))
                .clip(RoundedCornerShape(r))
                .background(Color.Black)
        ) {
            Crossfade(targetState = ui, label = "ui") { s ->
                when (s) {
                    Ui.Idle -> Spacer(Modifier.size(0.dp))
                    is Ui.Compact -> CompactContent(s.main) { manualKey = s.main.key }
                    is Ui.Expanded -> ExpandedContent(s.act) {
                        manualKey = null
                        IslandStore.clearPulse(s.act.seq)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = secondary != null,
            enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            shownSecond.value?.let { s ->
                Row {
                    Spacer(Modifier.width(6.dp))
                    SecondCircle(s) { manualKey = s.key }
                }
            }
        }
    }
}

private fun openIntent(a: IslandActivity) {
    runCatching { a.contentIntent?.send() }
}

@Composable
private fun CompactContent(a: IslandActivity, onExpand: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 11.dp)
            .pointerInput(a.key) {
                detectTapGestures(onTap = { openIntent(a) }, onLongPress = { onExpand() })
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Leading(a, 22.dp)
        Trailing(a)
    }
}

@Composable
private fun SecondCircle(a: IslandActivity, onExpand: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .pointerInput(a.key) {
                detectTapGestures(onTap = { openIntent(a) }, onLongPress = { onExpand() })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (a.kind == Kind.CALL) Leading(a, 20.dp) else Trailing(a)
    }
}

@Composable
private fun Leading(a: IslandActivity, size: Dp) {
    val bmp = a.art ?: a.icon
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)),
        )
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF3A3A3C)))
    }
}

@Composable
private fun Trailing(a: IslandActivity) {
    when (a.kind) {
        Kind.MEDIA -> Equalizer(a.playing)
        Kind.PROGRESS -> ProgressRing(a.progress / 100f, 20.dp)
        Kind.TIMER -> TimerText(a, 13)
        Kind.CALL -> Text("Chamada", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Kind.NOTIFICATION -> Box(Modifier.size(8.dp).clip(CircleShape).background(Red))
    }
}

@Composable
private fun ExpandedContent(a: IslandActivity, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(a.seq) { detectVerticalDragGestures { _, dy -> if (dy < -12f) onClose() } }
            .pointerInput(a.seq) { detectTapGestures(onTap = { openIntent(a); onClose() }) }
            .padding(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Leading(a, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (a.text.isNotEmpty()) {
                    Text(
                        a.text, color = Color(0xFFAEAEB2), fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when (a.kind) {
                Kind.MEDIA -> Equalizer(a.playing)
                Kind.TIMER -> TimerText(a, 22)
                Kind.PROGRESS -> ProgressRing(a.progress / 100f, 28.dp)
                else -> {}
            }
        }

        if (a.progress >= 0 && a.kind != Kind.MEDIA) {
            Spacer(Modifier.height(12.dp))
            Bar(a.progress / 100f)
        }

        a.controller?.let { c ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(Glyph.PREV) { c.transportControls.skipToPrevious() }
                GlyphButton(if (a.playing) Glyph.PAUSE else Glyph.PLAY) {
                    if (a.playing) c.transportControls.pause() else c.transportControls.play()
                }
                GlyphButton(Glyph.NEXT) { c.transportControls.skipToNext() }
            }
        }

        if (a.kind != Kind.MEDIA && a.actions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                a.actions.forEach { act -> ActionPill(act, actionColor(act, a.kind), onClose) }
            }
        }
    }
}

private val POS = Regex("atend|aceit|answer|accept", RegexOption.IGNORE_CASE)
private val NEG = Regex("recus|rejeit|declin|reject|encerr|desliga|end|hang", RegexOption.IGNORE_CASE)

private fun actionColor(a: IslandAction, kind: Kind): Color = when {
    kind != Kind.CALL -> Gray
    POS.containsMatchIn(a.label) -> Green
    NEG.containsMatchIn(a.label) -> Red
    else -> Gray
}

@Composable
private fun RowScope.ActionPill(act: IslandAction, bg: Color, onDone: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { runCatching { act.intent?.send() }; onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            act.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Bar(p: Float) {
    val v by animateFloatAsState(p.coerceIn(0f, 1f), label = "bar")
    Box(
        Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))
    ) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().background(Blue))
    }
}

@Composable
private fun Equalizer(active: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(
        Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(420, 560, 480).forEach { d ->
            val f by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "b",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (active) f else 0.25f)
                    .background(Green, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun ProgressRing(p: Float, diameter: Dp) {
    val v by animateFloatAsState(p.coerceIn(0f, 1f), label = "ring")
    Canvas(Modifier.size(diameter)) {
        val stroke = 3.dp.toPx()
        val tl = Offset(stroke / 2, stroke / 2)
        val sz = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(Color(0x33FFFFFF), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(stroke))
        drawArc(Blue, -90f, 360f * v, false, topLeft = tl, size = sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun TimerText(a: IslandActivity, sp: Int) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a.key) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val ms = if (a.countDown) (a.whenMs - now).coerceAtLeast(0) else (now - a.whenMs).coerceAtLeast(0)
    val s = ms / 1000
    val txt = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
    Text(txt, color = Orange, fontSize = sp.sp, fontWeight = FontWeight.SemiBold)
}

private enum class Glyph { PLAY, PAUSE, NEXT, PREV }

@Composable
private fun GlyphButton(g: Glyph, onClick: () -> Unit) {
    Canvas(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        val w = size.width
        val h = size.height
        val c = Color.White
        when (g) {
            Glyph.PLAY -> drawPath(Path().apply {
                moveTo(w * 0.1f, 0f); lineTo(w, h / 2); lineTo(w * 0.1f, h); close()
            }, c)
            Glyph.PAUSE -> {
                drawRect(c, Offset(w * 0.08f, 0f), Size(w * 0.3f, h))
                drawRect(c, Offset(w * 0.62f, 0f), Size(w * 0.3f, h))
            }
            Glyph.NEXT -> {
                drawPath(Path().apply {
                    moveTo(0f, 0f); lineTo(w * 0.75f, h / 2); lineTo(0f, h); close()
                }, c)
                drawRect(c, Offset(w * 0.8f, 0f), Size(w * 0.2f, h))
            }
            Glyph.PREV -> {
                drawPath(Path().apply {
                    moveTo(w, 0f); lineTo(w * 0.25f, h / 2); lineTo(w, h); close()
                }, c)
                drawRect(c, Offset(0f, 0f), Size(w * 0.2f, h))
            }
        }
    }
}
