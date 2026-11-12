package com.bitdroid.dialer.call

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.dialer.data.Phone
import com.bitdroid.dialer.data.Prefs
import com.bitdroid.dialer.ui.Avatar
import com.bitdroid.dialer.ui.Bit
import com.bitdroid.dialer.ui.BitTheme
import com.bitdroid.dialer.ui.Display
import com.bitdroid.dialer.ui.ToggleMark
import com.bitdroid.dialer.ui.monetGlow
import com.bitdroid.dialer.ui.mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Full-screen call UI. Shows over the lock screen and turns the screen on for incoming calls. */
class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        handle(intent)
        setContent {
            BitTheme {
                val calls by CallManager.calls.collectAsStateWithLifecycle()
                val audio by CallManager.audio.collectAsStateWithLifecycle()
                val primary = CallManager.primary(calls)
                LaunchedEffect(calls) {
                    if (calls.isEmpty() || calls.all { it.ended }) {
                        delay(1200)
                        if (CallManager.calls.value.let { it.isEmpty() || it.all { c -> c.ended } }) finishAndRemoveTask()
                    }
                }
                val earpiece = audio == null || audio!!.route == CallAudioState.ROUTE_EARPIECE
                LaunchedEffect(primary?.active, earpiece) { setProximity(primary?.active == true && earpiece) }
                Box(Modifier.fillMaxSize().background(Bit.Black).monetGlow(Prefs.monetBackground(this@InCallActivity), strong = true)) {
                    if (primary != null) CallScreen(primary, calls, audio)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == CallNotifier.ACTION_ANSWER) {
            CallManager.calls.value.firstOrNull { it.ringing }?.let(CallManager::answer)
        }
    }

    private fun setProximity(on: Boolean) {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        if (on) {
            if (proximity == null) proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "bitDialer:proximity")
            if (proximity?.isHeld == false) proximity?.acquire(4 * 60 * 60 * 1000L)
        } else {
            proximity?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }
}

@Composable
private fun CallScreen(c: UiCall, all: List<UiCall>, audio: CallAudioState?) {
    val context = LocalContext.current
    val name = c.contact.name ?: Phone.format(context, c.number).ifBlank { "Unknown" }
    val sub = buildList {
        if (c.contact.name != null && c.number.isNotBlank()) add(Phone.format(context, c.number))
        c.call.details?.accountHandle?.let { h ->
            context.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts?.let { list ->
                if (list.size > 1) add("SIM ${list.indexOf(h) + 1}")
            }
        }
    }.joinToString(" · ").uppercase()

    if (c.ringing) Incoming(c, name, sub) else Ongoing(c, all, name, sub, audio)
}

@Composable
private fun Brand(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToggleMark()
        Spacer(Modifier.width(10.dp))
        Text(label, style = mono(12f, color = Bit.Accent, spacing = 1.6f))
    }
}

@Composable
private fun RingedAvatar(c: UiCall, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Bit.Accent), contentAlignment = Alignment.Center) {
        Avatar(listOf(c.contact), (size - size / 14).dp, Modifier.border(4.dp, Bit.Black, CircleShape))
    }
}

// ------------------------------------------------------------- incoming

@Composable
private fun Incoming(c: UiCall, name: String, sub: String) {
    val context = LocalContext.current
    val view = LocalView.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "t")

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Brand("INCOMING CALL")
        Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            listOf(0f, 0.33f, 0.66f).forEach { phase ->
                val p = (t + phase) % 1f
                Box(
                    Modifier.size((190 + 130 * p).dp).graphicsLayer { alpha = (1f - p) * 0.45f }
                        .border(2.dp, Bit.Accent, CircleShape),
                )
            }
            RingedAvatar(c, 180)
        }
        Text(name, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 38.sp, letterSpacing = (-1.3).sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(sub, style = mono(12f, spacing = 1f))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallAction(Icons.AutoMirrored.Rounded.Message, "REPLY") {
                CallManager.decline(c)
                if (c.number.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            SmallAction(Icons.Rounded.NotificationsOff, "SILENCE") { CallManager.silenceRinger(context) }
        }
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Bit.Danger).clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    CallManager.decline(c)
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(30.dp)) }
            SlideToAnswer(Modifier.weight(1f)) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                CallManager.answer(c)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SlideToAnswer(modifier: Modifier, onAnswer: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.height(72.dp).clip(CircleShape).border(2.5.dp, Color.White, CircleShape)
            .semantics {
                contentDescription = "Answer"
                onClick { onAnswer(); true }
            },
    ) {
        val knob = with(density) { 56.dp.toPx() }
        val pad = with(density) { 8.dp.toPx() }
        val max = with(density) { maxWidth.toPx() } - knob - pad * 2
        Text(
            "SLIDE TO ANSWER →",
            style = mono(12f, color = Color.White, spacing = 1.6f),
            modifier = Modifier.align(Alignment.Center).padding(start = 56.dp).graphicsLayer { alpha = 1f - (offset.value / max).coerceIn(0f, 1f) },
        )
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(56.dp).clip(CircleShape).background(Bit.Accent)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(0f, max)) } },
                    onDragStopped = {
                        if (offset.value > max * 0.75f) {
                            offset.animateTo(max)
                            onAnswer()
                        } else {
                            offset.animateTo(0f, Bit.bouncy)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Phone, null, tint = Bit.OnAccent, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Bit.Glass).border(1.5.dp, Bit.GlassEdge, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = mono(10.5f, spacing = 1f))
    }
}

// --------------------------------------------------------------- ongoing

@Composable
private fun Ongoing(c: UiCall, all: List<UiCall>, name: String, sub: String, audio: CallAudioState?) {
    val context = LocalContext.current
    val view = LocalView.current
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(c.active) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val speaker = route == CallAudioState.ROUTE_SPEAKER
    val bt = route == CallAudioState.ROUTE_BLUETOOTH
    val others = all.filter { it !== c && !it.ended }

    val status = when {
        c.ended -> c.disconnectLabel?.uppercase()?.ifBlank { null } ?: "CALL ENDED"
        c.selectingSim -> "CHOOSE A SIM"
        c.dialing -> "CALLING…"
        c.holding -> "ON HOLD"
        c.active && c.connectTime > 0 -> {
            val s = ((now - c.connectTime) / 1000).coerceAtLeast(0)
            if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
        }
        else -> "CONNECTED"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Brand(if (c.ended) "CALL ENDED" else if (c.dialing) "CALLING" else "ON CALL")
        Spacer(Modifier.height(if (keypad) 18.dp else 34.dp))
        if (!keypad) RingedAvatar(c, 128)
        Spacer(Modifier.height(16.dp))
        Text(name, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = if (keypad) 26.sp else 34.sp, letterSpacing = (-1.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
        if (sub.isNotBlank() && !keypad) {
            Spacer(Modifier.height(6.dp))
            Text(sub, style = mono(11.5f, spacing = 1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(status, style = mono(if (c.active) 28f else 15f, color = if (c.ended) Bit.Error else Bit.Accent, spacing = 2f))

        others.firstOrNull()?.let { o ->
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.clip(CircleShape).background(Bit.Glass).border(1.dp, Bit.GlassEdge, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text((o.contact.name ?: Phone.format(context, o.number)), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(if (o.holding) "ON HOLD" else if (o.ringing) "WAITING" else "ACTIVE", style = mono(10.5f))
            }
        }

        if (c.selectingSim) {
            Spacer(Modifier.height(30.dp))
            val tm = context.getSystemService(TelecomManager::class.java)
            tm?.callCapablePhoneAccounts.orEmpty().forEachIndexed { i, h ->
                val label = tm?.getPhoneAccount(h)?.label?.toString() ?: "SIM ${i + 1}"
                Row(
                    Modifier.padding(horizontal = 28.dp, vertical = 6.dp).fillMaxWidth().height(60.dp).clip(CircleShape)
                        .border(2.dp, Bit.GlassEdge, CircleShape).clickable { CallManager.chooseSim(c, h) }.padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SimCard, null, tint = Bit.Accent)
                    Spacer(Modifier.width(12.dp))
                    Text("SIM ${i + 1} · $label", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        } else if (keypad) {
            Spacer(Modifier.height(12.dp))
            Text(typed.ifEmpty { " " }, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            DtmfPad { d ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                typed += d
                CallManager.dtmf(c, d)
            }
        } else if (!c.ended) {
            Spacer(Modifier.height(40.dp))
            val controls = buildList {
                add(Ctrl("MUTE", Icons.Rounded.MicOff, muted) { CallManager.setMuted(!muted) })
                add(Ctrl("KEYPAD", Icons.Rounded.Dialpad, false) { keypad = true })
                if (audio != null && audio.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                    add(Ctrl(if (bt) "BLUETOOTH" else if (speaker) "SPEAKER" else "AUDIO", if (bt) Icons.Rounded.Bluetooth else Icons.AutoMirrored.Rounded.VolumeUp, speaker || bt) {
                        val next = when (route) {
                            CallAudioState.ROUTE_BLUETOOTH -> CallAudioState.ROUTE_SPEAKER
                            CallAudioState.ROUTE_SPEAKER -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
                            else -> CallAudioState.ROUTE_BLUETOOTH
                        }
                        CallManager.service?.setAudioRoute(next)
                    })
                } else {
                    add(Ctrl("SPEAKER", Icons.AutoMirrored.Rounded.VolumeUp, speaker) { CallManager.setSpeaker(!speaker) })
                }
                add(Ctrl("ADD CALL", Icons.Rounded.Add, false) {
                    context.startActivity(Intent(Intent.ACTION_DIAL).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                if (others.any { it.holding } && c.active) {
                    add(Ctrl("MERGE", Icons.Rounded.CallMerge, false) { CallManager.merge(all) })
                    add(Ctrl("SWAP", Icons.Rounded.SwapCalls, false) { CallManager.swap(all) })
                } else {
                    add(Ctrl("HOLD", Icons.Rounded.Pause, c.holding) { CallManager.toggleHold(c) })
                    add(Ctrl("MESSAGE", Icons.AutoMirrored.Rounded.Message, false) {
                        if (c.number.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                }
            }
            controls.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { ControlButton(it) }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (keypad) {
            Text(
                "HIDE KEYPAD", style = mono(12f, color = Color.White, spacing = 1.4f),
                modifier = Modifier.clip(CircleShape).clickable { keypad = false }.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        if (!c.ended) {
            Row(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(72.dp).clip(CircleShape).background(Bit.Danger)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        CallManager.hangup(c)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("END CALL", style = mono(13f, color = Color.White, spacing = 1.6f))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private data class Ctrl(val label: String, val icon: ImageVector, val on: Boolean, val onClick: () -> Unit)

@Composable
private fun ControlButton(c: Ctrl) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(
            Modifier.size(78.dp).clip(CircleShape)
                .then(if (c.on) Modifier.background(Bit.Accent) else Modifier.background(Bit.Glass).border(1.5.dp, Bit.GlassEdge, CircleShape))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    c.onClick()
                },
            contentAlignment = Alignment.Center,
        ) { Icon(c.icon, c.label, tint = if (c.on) Bit.OnAccent else Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(9.dp))
        Text(c.label, style = mono(10.5f, color = if (c.on) Bit.Accent else Bit.TextDim, spacing = 1f))
    }
}

@Composable
private fun DtmfPad(onDigit: (Char) -> Unit) {
    val keys = listOf("123", "456", "789", "*0#")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { d ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(Bit.Glass).border(1.5.dp, Bit.GlassEdge, CircleShape).clickable { onDigit(d) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$d", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp) }
                }
            }
        }
    }
}
