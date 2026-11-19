package com.bitdroid.dialer.call

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.dialer.data.Contact
import com.bitdroid.dialer.ui.Avatar
import com.bitdroid.dialer.ui.Bit
import com.bitdroid.dialer.ui.GlassCircle
import com.bitdroid.dialer.ui.glass
import com.bitdroid.dialer.ui.glassShine
import com.bitdroid.dialer.ui.glassSweep
import com.bitdroid.dialer.ui.mono
import com.bitdroid.dialer.ui.squish
import kotlin.math.roundToInt
import kotlin.math.sin

// Fruit call screens: black with colored light, glass controls, gradient accents.

private val Green = Brush.linearGradient(listOf(Color(0xFF3DDC97), Color(0xFF1FA37A)))
private val Red = Brush.linearGradient(listOf(Color(0xFFFF6B81), Color(0xFFE0344F)))

@Composable
private fun RingAvatar(contact: Contact, size: Int) {
    Box(Modifier.size((size + 12).dp).clip(CircleShape).background(Bit.Gradient).padding(3.dp).clip(CircleShape).background(Color.Black).padding(3.dp)) {
        Avatar(listOf(contact), size.dp)
    }
}

@Composable
internal fun FruitIncoming(
    contact: Contact,
    name: String,
    sim: String?,
    subtitle: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit,
    onSilence: () -> Unit,
) {
    val view = LocalView.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(0.85f, 1.08f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
    var silenced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Text(listOfNotNull("INCOMING", sim).joinToString(" · "), style = mono(12f, color = Bit.Info, spacing = 1.5f))
        Spacer(Modifier.height(16.dp))
        Text(name, fontSize = if (name.length > 14) 30.sp else 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
        Text(subtitle, color = Color(0xFFC8C8D0), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(46.dp))
        Box(Modifier.graphicsLayer { scaleX = glow; scaleY = glow }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(190.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF6E64FF).copy(alpha = 0.55f), Color.Transparent))))
            RingAvatar(contact, 150)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceAround) {
            SmallAction(Icons.AutoMirrored.Rounded.Message, "Message", onMessage)
            SmallAction(Icons.Rounded.NotificationsOff, if (silenced) "Silenced" else "Silence") { silenced = true; onSilence() }
        }
        Spacer(Modifier.height(26.dp))
        AnswerSlider(
            onAnswer = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onAnswer() },
            onDecline = { view.performHapticFeedback(HapticFeedbackConstants.REJECT); onDecline() },
        )
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(58.dp, onClick = onClick) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color(0xFFC8C8D0), fontSize = 13.sp)
    }
}

/** Glass track: drag the green knob to the right to answer, tap red to decline. */
@Composable
private fun AnswerSlider(onAnswer: () -> Unit, onDecline: () -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    var answered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    fun answer() {
        if (answered) return
        answered = true
        onAnswer()
    }
    BoxWithConstraints(Modifier.padding(horizontal = 22.dp).fillMaxWidth().height(84.dp).glass(CircleShape).glassSweep()) {
        val knob = with(density) { 66.dp.toPx() }
        val pad = with(density) { 9.dp.toPx() }
        val max = (with(density) { maxWidth.toPx() } - knob * 2 - pad * 2).coerceAtLeast(1f)
        val progress = (drag / max).coerceIn(0f, 1f)
        Text(
            "SLIDE TO ANSWER ›", style = mono(12.5f, color = Color(0xFFC8C8D0), spacing = 1.5f),
            modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - progress },
        )
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 9.dp).size(66.dp).squish(onClick = onDecline).clip(CircleShape).background(Red).glassShine(0.35f),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(28.dp)) }
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 9.dp)
                .offset { IntOffset(drag.roundToInt(), 0) }
                .size(66.dp).clip(CircleShape).background(Green).glassShine(0.4f)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        drag = (drag + d).coerceIn(0f, max)
                        if (drag >= max * 0.9f) answer()
                    },
                    onDragStopped = {
                        if (drag > max * 0.6f) {
                            answer()
                            drag = max
                        } else {
                            androidx.compose.animation.core.animate(drag, 0f, animationSpec = Bit.bouncy) { v, _ -> drag = v }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Call, "Answer", tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
}

// ============================================================= ongoing

@Composable
internal fun FruitOngoing(s: BloomCallState, a: BloomCallActions) {
    val view = LocalView.current
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    val live = s.active && !s.muted && !s.holding

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            RingAvatar(s.contact, 58)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(s.timer ?: s.status.uppercase(), if (s.active && !s.holding) "HD" else null, if (s.holding) "ON HOLD" else null, s.sim).joinToString(" · "),
                    style = mono(13f, color = if (s.ended) Bit.Error else Bit.Info, spacing = 0.5f), modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (!s.ended) GlassCircle(44.dp, onClick = a.onMessage) { Icon(Icons.AutoMirrored.Rounded.Message, "Message", tint = Color.White, modifier = Modifier.size(19.dp)) }
        }
        s.other?.let {
            Text(it.uppercase(), style = mono(11f, color = Bit.TextDim, spacing = 0.5f), modifier = Modifier.padding(start = 24.dp, top = 10.dp))
        }
        if (!keypad) VoiceBars(live, Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp).fillMaxWidth().height(44.dp))

        when {
            s.simChoices.isNotEmpty() -> {
                Spacer(Modifier.height(30.dp))
                Text("CHOOSE A SIM", style = mono(11.5f, color = Bit.Info, spacing = 1f), modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
                s.simChoices.forEach { (label, pick) ->
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().height(64.dp).squish { pick() }.glass(CircleShape).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SimCard, null, tint = Bit.Info)
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            keypad -> {
                Text(typed.ifEmpty { " " }, fontSize = 30.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 24.dp, top = 20.dp))
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("123", "456", "789", "*0#").forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { d ->
                                GlassCircle(76.dp, onClick = { tick(); typed += d; a.onDigit(d) }) {
                                    Text("$d", fontSize = 30.sp, fontWeight = FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.align(Alignment.CenterHorizontally).height(44.dp).squish { keypad = false }.glass(CircleShape).padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("HIDE KEYPAD", style = mono(12f, color = Color.White, spacing = 1f)) }
            }
            else -> {
                Spacer(Modifier.weight(1f))
                if (!s.ended) {
                    val controls = buildList {
                        add(Control(Icons.Rounded.MicOff, "Mute", s.muted, a.onMute))
                        add(Control(Icons.Rounded.Dialpad, "Keypad", false) { keypad = true })
                        add(Control(if (s.bluetooth) Icons.Rounded.Bluetooth else Icons.AutoMirrored.Rounded.VolumeUp, if (s.bluetooth) "Bluetooth" else "Speaker", s.speaker || s.bluetooth, a.onAudio))
                        if (s.canMerge) {
                            add(Control(Icons.Rounded.SwapCalls, "Swap", false, a.onSwap))
                            add(Control(Icons.Rounded.CallMerge, "Merge", false, a.onMerge))
                        } else {
                            add(Control(Icons.Rounded.Add, "Add call", false, a.onAdd))
                            add(Control(if (s.holding) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (s.holding) "Resume" else "Hold", s.holding, a.onHold))
                        }
                        add(Control(Icons.AutoMirrored.Rounded.Message, "Message", false, a.onMessage))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        controls.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                row.forEach { c -> ControlButton(c) { tick(); c.onClick() } }
                            }
                        }
                    }
                }
            }
        }

        if (!s.ended) {
            Row(
                Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 26.dp).fillMaxWidth().height(74.dp)
                    .squish {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        a.onHangup()
                    }
                    .clip(CircleShape).background(Red).glassShine(0.35f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Text("End call", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (s.timer != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(s.timer, style = mono(12f, color = Color.White.copy(alpha = 0.75f), spacing = 0.5f))
                }
            }
        } else {
            Spacer(Modifier.height(40.dp))
        }
    }
}

private class Control(val icon: ImageVector, val label: String, val on: Boolean, val onClick: () -> Unit)

@Composable
private fun ControlButton(c: Control, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier.size(80.dp).squish(onClick = onClick)
                .then(if (c.on) Modifier.clip(shape).background(Bit.Gradient).glassShine(0.4f) else Modifier.glass(shape)),
            contentAlignment = Alignment.Center,
        ) { Icon(c.icon, c.label, tint = Color.White, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(c.label.uppercase(), style = mono(10.5f, color = if (c.on) Bit.Info else Color(0xFFB8B8C2), spacing = 1f))
    }
}

/** Gradient bars that dance while the line is live and settle when muted or on hold. */
@Composable
private fun VoiceBars(live: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.LinearEasing)), label = "p")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        repeat(24) { i ->
            val h = if (live) 0.2f + 0.8f * (0.5f + 0.5f * sin(phase * 2 + i * 0.7f) * sin(phase + i * 0.31f)).coerceIn(0f, 1f) else 0.12f
            Box(
                Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(3.dp)).background(Bit.Gradient)
                    .graphicsLayer { alpha = 0.35f + h * 0.6f },
            )
        }
    }
}
