package com.bitdroid.dialer.call

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.dialer.data.Contact
import com.bitdroid.dialer.ui.Avatar
import com.bitdroid.dialer.ui.Bit
import com.bitdroid.dialer.ui.Body
import com.bitdroid.dialer.ui.Shapes
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// Bloom call screens: laid out like the bitDroid home screen widgets.

private val Ink = Color(0xFFEDE6FF)
private val EndPink = Color(0xFFF2B8C8)
private val EndInk = Color(0xFF5E1130)
private val Green = Color(0xFF9BE7C4)

/** Name in light + bold, like the home screen clock. */
private fun splitName(name: String) = buildAnnotatedString {
    pushStyle(SpanStyle(fontWeight = FontWeight.Light)); append(name.take(1)); pop()
    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)); append(name.drop(1)); pop()
}

/** "4:12" → light "4:" + bold "12". */
private fun splitTime(t: String) = buildAnnotatedString {
    val i = t.lastIndexOf(':')
    if (i < 0) {
        pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)); append(t); pop()
    } else {
        pushStyle(SpanStyle(fontWeight = FontWeight.ExtraLight)); append(t.substring(0, i + 1)); pop()
        pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)); append(t.substring(i + 1)); pop()
    }
}

// ============================================================ incoming

@Composable
internal fun BloomIncoming(
    contact: Contact,
    number: String,
    name: String,
    sim: String?,
    subtitle: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit,
    onSilence: () -> Unit,
) {
    val view = LocalView.current
    var silenced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.clip(CircleShape).background(Bit.Pink).padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).clip(Shapes.Sunny).background(Bit.PinkText))
            Spacer(Modifier.width(8.dp))
            Text(listOfNotNull("Incoming call", sim).joinToString(" · "), color = Bit.PinkText, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        // The clock-widget tile: name + number, with the avatar in the date ripple.
        Box(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(44.dp)).background(Bit.Tile)) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 26.dp, end = 160.dp)) {
                Text(
                    splitName(name), color = Ink, fontFamily = Body, fontSize = if (name.length > 9) 38.sp else 54.sp,
                    letterSpacing = (-2).sp, lineHeight = if (name.length > 9) 40.sp else 54.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(subtitle, color = Bit.TextDim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.align(Alignment.CenterEnd).offset(x = 8.dp)) { BloomAvatar(contact, name, 172) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(34.dp)).background(Bit.Accent).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(46.dp).clip(Shapes.Clover).background(Bit.OnAccent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SimCard, null, tint = Bit.Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Via", color = Bit.OnAccent.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(sim ?: "Mobile", color = Bit.OnAccent, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(34.dp)).background(Bit.Pink)
                    .clickable {
                        silenced = true
                        onSilence()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(Shapes.Sunny).background(Bit.PinkText), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NotificationsOff, null, tint = Bit.Pink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (silenced) "Ringer" else "Tap to", color = Bit.PinkText.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (silenced) "Silenced" else "Silence", color = Bit.PinkText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundAction("Decline", Modifier.clip(CircleShape).background(EndPink), {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                onDecline()
            }) { Icon(Icons.Rounded.CallEnd, "Decline", tint = EndInk, modifier = Modifier.size(30.dp)) }
            BloomSlideUp(Modifier.width(112.dp).height(290.dp)) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onAnswer()
            }
            RoundAction("Message", Modifier.clip(Shapes.Squircle).background(Bit.Tile), onMessage) {
                Icon(Icons.AutoMirrored.Rounded.Message, "Message", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoundAction(label: String, shape: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).then(shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BloomAvatar(contact: Contact, name: String, size: Int) {
    if (contact.photoUri != null || contact.name == null) {
        Avatar(listOf(contact), size.dp, shape = Shapes.Ripple)
    } else {
        Box(Modifier.size(size.dp).clip(Shapes.Ripple).background(Bit.Accent), contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "#", color = Bit.OnAccent, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.4f).sp)
        }
    }
}

/** A tall pill track; drag the sunburst up to answer. */
@Composable
private fun BloomSlideUp(modifier: Modifier, onAnswer: () -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }
    val bob = rememberInfiniteTransition(label = "bob")
    val nudge by bob.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n")
    val spin by bob.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "s")
    val density = LocalDensity.current
    fun answer() {
        if (answered) return
        answered = true
        onAnswer()
    }
    BoxWithConstraints(
        modifier.clip(CircleShape).background(Color(0xBF4A4264))
            .border(2.5.dp, Bit.Accent.copy(alpha = 0.45f), CircleShape)
            .semantics {
                contentDescription = "Answer"
                onClick { answer(); true }
            },
    ) {
        val knob = with(density) { 90.dp.toPx() }
        val pad = with(density) { 11.dp.toPx() }
        val max = (with(density) { maxHeight.toPx() } - knob - pad * 2).coerceAtLeast(1f)
        val progress = (drag / max).coerceIn(0f, 1f)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp).graphicsLayer { alpha = 1f - progress },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Bit.TextDim, modifier = Modifier.size(26.dp))
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Bit.TextDim.copy(alpha = 0.6f), modifier = Modifier.size(26.dp).offset(y = (-12).dp))
            Text("Slide up", color = Bit.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        val bobPx = if (dragging || drag > 0f) 0f else with(density) { 10.dp.toPx() } * nudge
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 11.dp)
                .offset { IntOffset(0, -(drag + bobPx).roundToInt()) }
                .size(90.dp)
                .clickable { answer() }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d ->
                        drag = (drag - d).coerceIn(0f, max)
                        if (drag >= max * 0.85f) answer()
                    },
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        if (drag > max * 0.6f) {
                            answer()
                            drag = max
                        } else {
                            androidx.compose.animation.core.animate(drag, 0f, animationSpec = Bit.bouncy) { v, _ -> drag = v }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.clip(Shapes.Sunny).background(Bit.Accent))
            Icon(Icons.Rounded.Phone, null, tint = Bit.OnAccent, modifier = Modifier.size(32.dp))
        }
    }
}

// ============================================================= ongoing

/** Everything the Bloom call screen shows; filled from a real call or the test call. */
internal data class BloomCallState(
    val name: String,
    val number: String,
    val contact: Contact,
    val sim: String?,
    val timer: String?,
    val status: String,
    val active: Boolean,
    val ended: Boolean,
    val muted: Boolean,
    val speaker: Boolean,
    val bluetooth: Boolean,
    val holding: Boolean,
    val canMerge: Boolean,
    val other: String? = null,
    val simChoices: List<Pair<String, () -> Unit>> = emptyList(),
)

internal class BloomCallActions(
    val onMute: () -> Unit,
    val onAudio: () -> Unit,
    val onHold: () -> Unit,
    val onAdd: () -> Unit,
    val onSwap: () -> Unit,
    val onMerge: () -> Unit,
    val onMessage: () -> Unit,
    val onDigit: (Char) -> Unit,
    val onHangup: () -> Unit,
)

@Composable
internal fun BloomOngoing(s: BloomCallState, a: BloomCallActions) {
    val view = LocalView.current
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        // Clock widget → call timer, with HD + SIM in the date ripple.
        Box(Modifier.fillMaxWidth().height(if (keypad) 128.dp else 176.dp).clip(RoundedCornerShape(46.dp)).background(Bit.Tile)) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 26.dp, end = 150.dp)) {
                if (s.timer != null) {
                    Text(splitTime(s.timer), color = Ink, fontFamily = Body, fontSize = if (keypad) 58.sp else if (s.timer.length > 5) 62.sp else 88.sp, letterSpacing = (-4).sp, lineHeight = 90.sp, maxLines = 1)
                } else {
                    Text(splitName(s.status), color = Ink, fontFamily = Body, fontSize = if (keypad) 30.sp else 38.sp, letterSpacing = (-1.5).sp, maxLines = 2)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.timer != null) (if (s.holding) "On hold" else "On a call") else (s.sim ?: "Mobile"),
                    color = if (s.ended) Bit.Error else Bit.TextDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier.align(Alignment.CenterEnd).offset(x = 10.dp).size(if (keypad) 120.dp else 168.dp).clip(Shapes.Ripple).background(Bit.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (s.active && !s.holding) {
                        Text("HD", color = Bit.OnAccent, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = if (keypad) 34.sp else 50.sp, lineHeight = 52.sp)
                    } else {
                        Icon(if (s.holding) Icons.Rounded.Pause else Icons.Rounded.PhoneInTalk, null, tint = Bit.OnAccent, modifier = Modifier.size(if (keypad) 34.dp else 48.dp))
                    }
                    Text((s.sim ?: "Call").uppercase(), color = Bit.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 3.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            s.simChoices.isNotEmpty() -> {
                Text("Choose a SIM", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                s.simChoices.forEach { (label, pick) ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).height(72.dp).clip(CircleShape).background(Bit.Accent).clickable { pick() }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(52.dp).clip(Shapes.Ripple).background(Bit.OnAccent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SimCard, null, tint = Bit.Accent)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(label, color = Bit.OnAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            keypad -> {
                Text(typed.ifEmpty { " " }, color = Ink, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.weight(1f))
                listOf("123", "456", "789", "*0#").forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { d ->
                            Box(
                                Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(24.dp)).background(Bit.Tile)
                                    .clickable {
                                        tick()
                                        typed += d
                                        a.onDigit(d)
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text("$d", color = Ink, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 28.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Bit.Accent).clickable { keypad = false },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Dialpad, null, tint = Bit.OnAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Hide keypad", color = Bit.OnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
            else -> {
                // Music widget → contact; alarm widget → audio; battery widget → voice line.
                Row(Modifier.fillMaxWidth().height(206.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(40.dp)).background(Bit.Tile)) {
                        Box(Modifier.padding(16.dp)) {
                            if (s.contact.photoUri != null) {
                                Avatar(listOf(s.contact), 90.dp, shape = Shapes.Ripple7)
                            } else {
                                Box(Modifier.size(90.dp).clip(Shapes.Ripple7).background(Bit.PinkText), contentAlignment = Alignment.Center) {
                                    Text(s.name.trim().firstOrNull()?.uppercase() ?: "#", color = Color(0xFF4A1E3E), fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp)
                                }
                            }
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(14.dp).size(54.dp).clip(CircleShape).background(Bit.Light).clickable { a.onMessage() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.Message, "Message", tint = Bit.OnAccent, modifier = Modifier.size(22.dp)) }
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 12.dp, bottom = 18.dp)) {
                            Text(s.name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (s.number.isNotBlank() && s.number != s.name) {
                                Text(s.number, color = Bit.TextDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(34.dp))
                                .background(if (s.speaker || s.bluetooth) Bit.Accent else Bit.Tile)
                                .clickable { tick(); a.onAudio() }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val on = s.speaker || s.bluetooth
                            Box(Modifier.size(48.dp).clip(Shapes.Ripple).background(if (on) Bit.OnAccent else Bit.Accent), contentAlignment = Alignment.Center) {
                                Icon(if (s.bluetooth) Icons.Rounded.Bluetooth else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = if (on) Bit.Accent else Bit.OnAccent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Audio", color = (if (on) Bit.OnAccent else Ink).copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(if (s.bluetooth) "Bluetooth" else if (s.speaker) "Speaker" else "Phone", color = if (on) Bit.OnAccent else Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            }
                        }
                        Row(
                            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Bit.Pink).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (s.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, null, tint = Bit.PinkText, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            VoiceSquiggle(live = s.active && !s.muted && !s.holding, modifier = Modifier.weight(1f).height(30.dp))
                        }
                    }
                }
                s.other?.let {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(CircleShape).background(Bit.Tile).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(22.dp).clip(Shapes.Sunny).background(Bit.PinkText))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(18.dp))
                // App icons → controls.
                if (!s.ended) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        AppIconButton(if (s.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Mute", s.muted) { tick(); a.onMute() }
                        AppIconButton(Icons.Rounded.Dialpad, "Keypad", false) { tick(); keypad = true }
                        if (s.canMerge) {
                            AppIconButton(Icons.Rounded.SwapCalls, "Swap", false) { tick(); a.onSwap() }
                            AppIconButton(Icons.Rounded.CallMerge, "Merge", false) { tick(); a.onMerge() }
                        } else {
                            AppIconButton(if (s.holding) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (s.holding) "Resume" else "Hold", s.holding) { tick(); a.onHold() }
                            AppIconButton(Icons.Rounded.Add, "Add", false) { tick(); a.onAdd() }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // "Find an app" bar → End call.
        if (!s.ended) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(72.dp).clip(CircleShape).background(Color(0xE65E3A5A))
                    .border(2.5.dp, EndPink.copy(alpha = 0.65f), CircleShape).padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        a.onHangup()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(EndPink), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CallEnd, null, tint = EndInk, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("End call", color = Bit.PinkText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Box {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable { more = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.MoreVert, "More", tint = Bit.PinkText, modifier = Modifier.size(22.dp)) }
                    DropdownMenu(more, { more = false }, containerColor = Bit.SurfaceHigh) {
                        DropdownMenuItem(text = { Text("Send message") }, onClick = { more = false; a.onMessage() })
                        DropdownMenuItem(text = { Text("Add call") }, onClick = { more = false; a.onAdd() })
                        DropdownMenuItem(text = { Text(if (s.speaker) "Speaker off" else "Speaker on") }, onClick = { more = false; a.onAudio() })
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Like the purple gradient app icons on the home screen; lavender when on. */
@Composable
private fun AppIconButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(26.dp))
                .background(
                    if (on) Brush.linearGradient(listOf(Color(0xFFECE2FF), Color(0xFFCBC2EB)))
                    else Brush.linearGradient(listOf(Color(0xFF6A4FD0), Color(0xFF3B3A8C)), start = Offset(0f, 0f), end = Offset(160f, 260f)),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Bit.OnAccent else Color(0xFFF2EEFF), modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** The battery-widget squiggle, wiggling while someone's talking and flat when muted. */
@Composable
private fun VoiceSquiggle(live: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "p")
    val amp by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val color = Bit.PinkText
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val a = if (live) h * 0.32f * amp else h * 0.06f
        val path = Path()
        val steps = 60
        for (i in 0..steps) {
            val x = w * i / steps
            val y = h / 2 + a * sin(phase + x / w * 2.2f * PI.toFloat() * 2).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
    }
}
