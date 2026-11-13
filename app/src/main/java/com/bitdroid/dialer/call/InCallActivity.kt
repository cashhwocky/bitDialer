package com.bitdroid.dialer.call

import com.bitdroid.dialer.ui.Body
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.bitdroid.dialer.ui.backdrop
import com.bitdroid.dialer.ui.Shapes
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
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.bitdroid.dialer.ui.Mono
import com.bitdroid.dialer.ui.mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/** Full-screen call UI. Shows over the lock screen and turns the screen on for incoming calls. */
class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null
    private var testMode = false

    companion object {
        const val EXTRA_TEST = "bitdialer_test_call"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        Bit.apply(this)
        testMode = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        Diag.log(this, if (testMode) "test call screen opened" else "call screen opened (calls=${CallManager.calls.value.size})")
        handle(intent)
        setContent {
            BitTheme {
                val calls by CallManager.calls.collectAsStateWithLifecycle()
                val audio by CallManager.audio.collectAsStateWithLifecycle()
                val primary = CallManager.primary(calls)
                LaunchedEffect(calls) {
                    if (testMode) return@LaunchedEffect
                    if (calls.isEmpty() || calls.all { it.ended }) {
                        delay(if (calls.isEmpty()) 5000 else 1200)
                        if (CallManager.calls.value.let { it.isEmpty() || it.all { c -> c.ended } }) finishAndRemoveTask()
                    }
                }
                val earpiece = audio == null || audio!!.route == CallAudioState.ROUTE_EARPIECE
                LaunchedEffect(primary?.active, earpiece) { setProximity(primary?.active == true && earpiece) }
                Box(Modifier.fillMaxSize().backdrop()) {
                    MovingGradient(useMonet = Prefs.monetBackground(this@InCallActivity), intense = primary?.ringing == true)
                    if (testMode && primary == null) {
                        TestCall()
                    } else if (primary != null) {
                        CallScreen(primary, calls, audio)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ test call

    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var voiceOn = false

    private val testLines = listOf(
        "Hey! You actually picked up. This is your test call from bit Dialer.",
        "Nobody is really here. It's just me, a very convincing robot, living inside your phone.",
        "Go ahead, try the buttons. Mute me, put me on hold, open the keypad. I promise I won't be offended.",
        "Fun fact. This whole screen is styled like your home screen. The timer is the clock, and I'm the battery squiggle.",
        "Okay, I'll keep rambling until you hang up. It's the pink button at the bottom. You know the one.",
    )

    private fun startVoice() {
        voiceOn = true
        val engine = tts
        if (engine == null) {
            tts = android.speech.tts.TextToSpeech(this) { status ->
                ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsReady && voiceOn) speakAll()
            }.also { t ->
                t.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) {}
                    override fun onDone(id: String?) {
                        // Loop the script for as long as the test call is up.
                        if (id == "last" && voiceOn) runOnUiThread { if (voiceOn) speakAll() }
                    }
                })
            }
        } else if (ttsReady) {
            speakAll()
        }
    }

    private fun speakAll() {
        val t = tts ?: return
        testLines.forEachIndexed { i, line ->
            t.speak(line, android.speech.tts.TextToSpeech.QUEUE_ADD, null, if (i == testLines.lastIndex) "last" else "l$i")
            t.playSilentUtterance(900, android.speech.tts.TextToSpeech.QUEUE_ADD, "gap$i")
        }
    }

    private fun stopVoice() {
        voiceOn = false
        tts?.stop()
    }

    @Composable
    private fun TestCall() {
        var answered by remember { mutableStateOf(false) }
        var answeredAt by remember { mutableLongStateOf(0L) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var muted by remember { mutableStateOf(false) }
        var speaker by remember { mutableStateOf(true) }
        var holding by remember { mutableStateOf(false) }
        val contact = com.bitdroid.dialer.data.Contact("", "Test call", null, null)
        LaunchedEffect(answered) {
            while (answered) {
                now = System.currentTimeMillis()
                delay(500)
            }
        }
        fun end() {
            stopVoice()
            Diag.log(this@InCallActivity, "test call ended")
            CallNotifier.cancelTest(this@InCallActivity)
            finishAndRemoveTask()
        }
        if (!answered) {
            val answer = {
                Diag.log(this@InCallActivity, "test call answered")
                CallNotifier.cancelTest(this@InCallActivity)
                answered = true
                answeredAt = System.currentTimeMillis()
                now = answeredAt
                startVoice()
            }
            if (Bit.expressive) {
                BloomIncoming(
                    contact = contact, number = "", name = "Test call", sim = "TEST", subtitle = "Answer to try the call screen",
                    onAnswer = answer, onDecline = { end() }, onMessage = { end() },
                    onSilence = { CallVibrator.stop(this@InCallActivity) },
                )
            } else {
                Incoming(contact = contact, number = "", name = "Test call", sim = "TEST", onAnswer = answer, onDecline = { end() })
            }
        } else {
            BloomOngoing(
                BloomCallState(
                    name = "Test call", number = "bitDialer robot", contact = contact, sim = "TEST",
                    timer = bloomClock(now - answeredAt), status = "Connected", active = true, ended = false,
                    muted = muted, speaker = speaker, bluetooth = false, holding = holding, canMerge = false,
                ),
                BloomCallActions(
                    onMute = { muted = !muted },
                    onAudio = { speaker = !speaker },
                    onHold = {
                        holding = !holding
                        if (holding) stopVoice() else startVoice()
                    },
                    onAdd = {},
                    onSwap = {},
                    onMerge = {},
                    onMessage = {},
                    onDigit = {},
                    onHangup = { end() },
                ),
            )
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
        stopVoice()
        tts?.shutdown()
        tts = null
        if (CallManager.calls.value.none { it.ringing }) CallVibrator.stop(this)
        setProximity(false)
        super.onDestroy()
    }
}

// =========================================================== background

/** Three soft color blobs drifting slowly behind the call, in Monet colors when that's on. */
@Composable
private fun MovingGradient(useMonet: Boolean, intense: Boolean) {
    val context = LocalContext.current
    val colors = remember(useMonet) {
        if (Bit.expressive) {
            listOf(Color(0xFF8C6EF0), Color(0xFFF28FB5), Color(0xFF2B9C9C))
        } else if (useMonet) {
            val d = dynamicDarkColorScheme(context)
            listOf(d.primary, d.tertiary, d.secondary)
        } else {
            listOf(Color(0xFF3E7CF5), Color(0xFF8E6CF0), Color(0xFF2BB5C9))
        }
    }
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(17000, easing = LinearEasing)), label = "b")
    val c by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19000, easing = LinearEasing)), label = "c")
    val strength = if (intense) 0.62f else 0.48f
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tau = (2 * Math.PI).toFloat()
        fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy),
            )
        }
        blob(colors[0], w * (0.15f + 0.18f * sin(a * tau)), h * (0.08f + 0.06f * sin(a * tau + 1f)), w * (0.95f + 0.1f * sin(a * tau)), strength)
        blob(colors[1], w * (0.95f + 0.15f * sin(b * tau + 2f)), h * (0.42f + 0.08f * sin(b * tau)), w * 0.9f, strength * 0.85f)
        blob(colors[2], w * (0.3f + 0.15f * sin(c * tau + 4f)), h * (0.98f + 0.05f * sin(c * tau + 1f)), w * 0.9f, strength * 0.7f)
        if (Bit.expressive) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF120F1C).copy(alpha = 0.45f), Color(0xFF0F1419).copy(alpha = 0.7f))))
        else drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.8f))))
    }
}

// ============================================================== shared

private fun phoneAccounts(context: android.content.Context): List<android.telecom.PhoneAccountHandle> = try {
    context.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts.orEmpty()
} catch (_: SecurityException) {
    emptyList()
}

@Composable
private fun CallScreen(c: UiCall, all: List<UiCall>, audio: CallAudioState?) {
    val context = LocalContext.current
    val name = c.contact.name ?: Phone.format(context, c.number).ifBlank { "Unknown" }
    val sim = c.call.details?.accountHandle?.let { h ->
        phoneAccounts(context).let { list -> if (list.size > 1) "SIM ${list.indexOf(h) + 1}" else null }
    }
    if (c.ringing) {
        if (Bit.expressive) {
            BloomIncoming(
                contact = c.contact, number = c.number, name = name, sim = sim,
                subtitle = if (c.contact.name != null) Phone.format(context, c.number) else "Unknown caller",
                onAnswer = { CallManager.answer(c) },
                onDecline = { CallManager.decline(c) },
                onMessage = {
                    CallManager.decline(c)
                    if (c.number.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                onSilence = { CallManager.silenceRinger(context) },
            )
        } else {
            Incoming(
                contact = c.contact, number = c.number, name = name, sim = sim,
                onAnswer = { CallManager.answer(c) },
                onDecline = { CallManager.decline(c) },
            )
        }
    } else if (Bit.expressive) {
        BloomOngoingFor(c, all, name, sim, audio)
    } else {
        Ongoing(c, all, name, sim, audio)
    }
}

/** Feeds a real call into the Bloom call screen. */
@Composable
private fun BloomOngoingFor(c: UiCall, all: List<UiCall>, name: String, sim: String?, audio: CallAudioState?) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val hasBt = audio != null && audio.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0
    val others = all.filter { it !== c && !it.ended }
    val timer = if (c.active && c.connectTime > 0) bloomClock(now - c.connectTime) else null
    val status = when {
        c.ended -> c.disconnectLabel?.ifBlank { null } ?: "Call ended"
        c.selectingSim -> "Choose a SIM"
        c.dialing -> "Calling…"
        c.holding -> "On hold"
        else -> "Connected"
    }
    val tm = context.getSystemService(TelecomManager::class.java)
    val state = BloomCallState(
        name = name,
        number = if (c.contact.name != null) Phone.format(context, c.number) else "",
        contact = c.contact, sim = sim, timer = timer, status = status,
        active = c.active, ended = c.ended, muted = audio?.isMuted == true,
        speaker = route == CallAudioState.ROUTE_SPEAKER, bluetooth = route == CallAudioState.ROUTE_BLUETOOTH,
        holding = c.holding, canMerge = others.any { it.holding } && c.active,
        other = others.firstOrNull()?.let { o -> (o.contact.name ?: Phone.format(context, o.number)) + " · " + if (o.holding) "on hold" else if (o.ringing) "waiting" else "active" },
        simChoices = if (c.selectingSim) phoneAccounts(context).mapIndexed { i, h ->
            val label = runCatching { tm?.getPhoneAccount(h)?.label?.toString() }.getOrNull() ?: "SIM ${i + 1}"
            "SIM ${i + 1} · $label" to { CallManager.chooseSim(c, h) }
        } else emptyList(),
    )
    BloomOngoing(
        state,
        BloomCallActions(
            onMute = { CallManager.setMuted(audio?.isMuted != true) },
            onAudio = {
                if (hasBt) {
                    CallManager.service?.setAudioRoute(
                        when (route) {
                            CallAudioState.ROUTE_BLUETOOTH -> CallAudioState.ROUTE_SPEAKER
                            CallAudioState.ROUTE_SPEAKER -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
                            else -> CallAudioState.ROUTE_BLUETOOTH
                        },
                    )
                } else {
                    CallManager.setSpeaker(route != CallAudioState.ROUTE_SPEAKER)
                }
            },
            onHold = { CallManager.toggleHold(c) },
            onAdd = { context.startActivity(Intent(Intent.ACTION_DIAL).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            onSwap = { CallManager.swap(all) },
            onMerge = { CallManager.merge(all) },
            onMessage = {
                if (c.number.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onDigit = { CallManager.dtmf(c, it) },
            onHangup = { CallManager.hangup(c) },
        ),
    )
}

/** "4:12", or "1:02:03" past an hour. */
internal fun bloomClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Rounded-square avatar with an accent frame and a softly breathing outline. */
@Composable
private fun SquircleAvatar(c: UiCall, size: Dp) = SquircleAvatar(c.contact, size)

@Composable
private fun SquircleAvatar(contact: com.bitdroid.dialer.data.Contact, size: Dp) {
    val t = rememberInfiniteTransition(label = "breathe")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val r = size * 0.36f
    Box(Modifier.size(size + 28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size + 28.dp).graphicsLayer { scaleX = 1f + 0.06f * p; scaleY = 1f + 0.06f * p; alpha = 0.45f + 0.45f * p }
                .border(2.dp, Bit.Accent.copy(alpha = 0.6f), RoundedCornerShape(r + 14.dp)),
        )
        Box(Modifier.size(size).clip(RoundedCornerShape(r)).background(Bit.Accent).padding(5.dp).clip(RoundedCornerShape(r - 5.dp))) {
            Avatar(listOf(contact), size - 10.dp, Modifier, square = true)
        }
    }
}

@Composable
private fun StatusTag(text: String, pulse: Boolean) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = if (pulse) a else 1f }.clip(CircleShape).background(Bit.Accent))
        Spacer(Modifier.width(8.dp))
        Text(text, style = mono(11.5f, color = Color.White, spacing = 1.4f))
    }
}

private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassLine = Color.White.copy(alpha = 0.17f)

// ============================================================ incoming

@Composable
private fun Incoming(
    contact: com.bitdroid.dialer.data.Contact,
    number: String,
    name: String,
    sim: String?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Spacer(Modifier.height(18.dp))
            StatusTag(listOfNotNull("INCOMING", sim).joinToString(" · "), pulse = true)
            Spacer(Modifier.height(32.dp))
            Box(Modifier.offset(x = (-14).dp)) { SquircleAvatar(contact, 150.dp) }
            Spacer(Modifier.height(20.dp))
            Text(name, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = if (name.length > 12) 40.sp else 56.sp, letterSpacing = (-2).sp, lineHeight = 58.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (contact.name != null && number.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(Phone.format(context, number).uppercase(), style = mono(13f, color = Color.White.copy(alpha = 0.7f), spacing = 1f))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPill(Icons.AutoMirrored.Rounded.Message, "REPLY", Modifier.weight(1f)) {
                onDecline()
                if (number.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            GlassPill(Icons.Rounded.NotificationsOff, "SILENCE", Modifier.weight(1f)) { CallManager.silenceRinger(context) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.width(112.dp).fillMaxHeight().clip(RoundedCornerShape(34.dp)).background(Bit.Danger)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        onDecline()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(12.dp))
                Text("DECLINE", style = mono(11.5f, color = Color.White, spacing = 1.4f))
            }
            SlideUpToAnswer(Modifier.weight(1f).fillMaxHeight()) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onAnswer()
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GlassPill(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(64.dp).clip(RoundedCornerShape(22.dp)).background(GlassFill).border(1.5.dp, GlassLine, RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = mono(12f, color = Color.White, spacing = 1.2f))
    }
}

@Composable
private fun SlideUpToAnswer(modifier: Modifier, onAnswer: () -> Unit) {
    // Plain state (not an Animatable) so drag updates can never cancel the release handling.
    var drag by remember { mutableFloatStateOf(0f) } // 0 = resting at the bottom, grows upward
    var dragging by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }
    val bob = rememberInfiniteTransition(label = "bob")
    val nudge by bob.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n")
    val density = LocalDensity.current
    fun answer() {
        if (answered) return
        answered = true
        onAnswer()
    }
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(34.dp)).background(Color.Black.copy(alpha = 0.35f))
            .border(2.5.dp, Color.White, RoundedCornerShape(34.dp))
            .semantics {
                contentDescription = "Answer"
                onClick { answer(); true }
            },
    ) {
        val knob = with(density) { 72.dp.toPx() }
        val pad = with(density) { 10.dp.toPx() }
        val max = (with(density) { maxHeight.toPx() } - knob - pad * 2).coerceAtLeast(1f)
        val progress = (drag / max).coerceIn(0f, 1f)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 16.dp).graphicsLayer { alpha = 1f - progress },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("↑", style = mono(13f, color = Color.White, spacing = 0f))
            Text("SLIDE UP", style = mono(11.5f, color = Color.White, spacing = 1.6f))
            Text("TO ANSWER", style = mono(11.5f, color = Color.White, spacing = 1.6f))
        }
        val bobPx = if (dragging || drag > 0f) 0f else with(density) { 8.dp.toPx() } * nudge
        Box(
            Modifier.align(Alignment.BottomCenter).padding(10.dp)
                .offset { IntOffset(0, -(drag + bobPx).roundToInt()) }
                .fillMaxWidth().height(72.dp).clip(RoundedCornerShape(26.dp)).background(Bit.Accent)
                .clickable { answer() }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d ->
                        drag = (drag - d).coerceIn(0f, max)
                        // Answer the moment it crosses the line, even before the finger lifts.
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
        ) { Icon(Icons.Rounded.Phone, null, tint = Bit.OnAccent, modifier = Modifier.size(30.dp)) }
    }
}

// ============================================================= ongoing

@Composable
private fun Ongoing(c: UiCall, all: List<UiCall>, name: String, sim: String?, audio: CallAudioState?) {
    val context = LocalContext.current
    val view = LocalView.current
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
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

    val timer = if (c.active && c.connectTime > 0) {
        val s = ((now - c.connectTime) / 1000).coerceAtLeast(0)
        if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    } else null
    val status = when {
        c.ended -> c.disconnectLabel?.uppercase()?.ifBlank { null } ?: "CALL ENDED"
        c.selectingSim -> "CHOOSE A SIM"
        c.dialing -> "CALLING…"
        c.holding -> "ON HOLD"
        else -> "CONNECTED"
    }

    val ex = Bit.expressive
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        if (ex) {
            HomeCallHeader(c, name, sim, timer, status, keypad)
        } else {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = (-10).dp)) { SquircleAvatar(c, 84.dp) }
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-1.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(listOfNotNull(if (c.active) "HD" else null, sim).joinToString(" · ").ifBlank { Phone.format(context, c.number).uppercase() }, style = mono(11.5f, color = Color.White.copy(alpha = 0.7f), spacing = 1f))
            }
        }

        Column(Modifier.padding(horizontal = 8.dp)) {
            Spacer(Modifier.height(if (keypad) 18.dp else 34.dp))
            if (timer != null) {
                Text(timer, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (keypad) 40.sp else 76.sp, letterSpacing = (-2).sp, lineHeight = 78.sp)
                if (!keypad) {
                    Spacer(Modifier.height(12.dp))
                    VoiceBars(active = !muted && !c.holding)
                }
            } else {
                Text(status, style = mono(if (keypad) 18f else 26f, color = if (c.ended) Bit.Error else Bit.Accent, spacing = 2f))
            }
        }
        }
        Column(Modifier.padding(horizontal = 8.dp).align(if (ex) Alignment.CenterHorizontally else Alignment.Start)) {
            others.firstOrNull()?.let { o ->
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(GlassFill).border(1.dp, GlassLine, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.contact.name ?: Phone.format(context, o.number), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (o.holding) "ON HOLD" else if (o.ringing) "WAITING" else "ACTIVE", style = mono(10.5f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when {
            c.selectingSim -> {
                val tm = context.getSystemService(TelecomManager::class.java)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    phoneAccounts(context).forEachIndexed { i, h ->
                        val label = runCatching { tm?.getPhoneAccount(h)?.label?.toString() }.getOrNull() ?: "SIM ${i + 1}"
                        Tile(Icons.Rounded.SimCard, "SIM ${i + 1}", label.uppercase(), false, Modifier.fillMaxWidth().height(80.dp)) { CallManager.chooseSim(c, h) }
                    }
                }
            }
            keypad -> {
                Text(typed.ifEmpty { " " }, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(10.dp))
                listOf("123", "456", "789", "*0#").forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { d ->
                            Box(
                                Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(22.dp)).background(GlassFill).border(1.5.dp, GlassLine, RoundedCornerShape(22.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        typed += d
                                        CallManager.dtmf(c, d)
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text("$d", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Tile(Icons.Rounded.Dialpad, "HIDE KEYPAD", null, false, Modifier.fillMaxWidth().height(60.dp)) { keypad = false }
            }
            !c.ended -> {
                val tiles = buildList {
                    add(TileSpec("MUTE", Icons.Rounded.MicOff, muted, if (muted) "ON" else "OFF") { CallManager.setMuted(!muted) })
                    if (audio != null && audio.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                        add(TileSpec(if (bt) "BLUETOOTH" else "AUDIO", if (bt) Icons.Rounded.Bluetooth else Icons.AutoMirrored.Rounded.VolumeUp, speaker || bt, if (bt) "ON" else if (speaker) "SPEAKER" else "PHONE") {
                            CallManager.service?.setAudioRoute(
                                when (route) {
                                    CallAudioState.ROUTE_BLUETOOTH -> CallAudioState.ROUTE_SPEAKER
                                    CallAudioState.ROUTE_SPEAKER -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
                                    else -> CallAudioState.ROUTE_BLUETOOTH
                                },
                            )
                        })
                    } else {
                        add(TileSpec("SPEAKER", Icons.AutoMirrored.Rounded.VolumeUp, speaker, if (speaker) "ON" else "OFF") { CallManager.setSpeaker(!speaker) })
                    }
                    add(TileSpec("KEYPAD", Icons.Rounded.Dialpad, false, null) { keypad = true })
                    if (others.any { it.holding } && c.active) {
                        add(TileSpec("MERGE", Icons.Rounded.CallMerge, false, null) { CallManager.merge(all) })
                        add(TileSpec("SWAP", Icons.Rounded.SwapCalls, false, null) { CallManager.swap(all) })
                    } else {
                        add(TileSpec("HOLD", Icons.Rounded.Pause, c.holding, if (c.holding) "ON" else null) { CallManager.toggleHold(c) })
                        add(TileSpec("ADD CALL", Icons.Rounded.Add, false, null) {
                            context.startActivity(Intent(Intent.ACTION_DIAL).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        })
                    }
                    add(TileSpec("MESSAGE", Icons.AutoMirrored.Rounded.Message, false, null) {
                        if (c.number.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                }
                tiles.chunked(3).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = if (ex) 8.dp else 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { i, s ->
                            if (ex) ShapeTile(s.icon, s.label, s.on, controlShapes[(r * 3 + i) % controlShapes.size], Modifier.weight(1f)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                s.onClick()
                            } else
                            Tile(s.icon, s.label, s.state, s.on, Modifier.weight(1f).height(96.dp)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                s.onClick()
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (!c.ended) {
            if (ex) Row(
                Modifier.fillMaxWidth().height(78.dp).clip(CircleShape).background(Color(0xFFF2B8C8))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        CallManager.hangup(c)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color(0xFF5E1130), modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Text("End call", color = Color(0xFF5E1130), fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            } else Row(
                Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(30.dp)).background(Bit.Danger)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        CallManager.hangup(c)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(14.dp))
                Text("END CALL", style = mono(13f, color = Color.White, spacing = 1.6f))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private val controlShapes = listOf(Shapes.Circle, Shapes.Squircle, Shapes.Clover, Shapes.Gem, Shapes.Pentagon, Shapes.Ripple7)

/** bitHome call header: status pill, a slowly spinning flower avatar, and the name in light + bold. */
@Composable
private fun HomeCallHeader(c: UiCall, name: String, sim: String?, timer: String?, status: String, compact: Boolean) {
    val context = LocalContext.current
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "angle")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.clip(CircleShape).background(Bit.Pink).padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).clip(Shapes.Sunny).background(if (c.ended) Bit.Error else if (c.active && !c.holding) Color(0xFF9BE7C4) else Bit.PinkText))
            Spacer(Modifier.width(8.dp))
            val text = if (timer != null) listOfNotNull(timer, if (c.holding) "On hold" else "HD Voice", sim).joinToString(" · ")
            else status.lowercase().replaceFirstChar { it.uppercase() }
            Text(text, color = Bit.PinkText, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        val size = if (compact) 110.dp else 214.dp
        Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }.clip(Shapes.Flower).background(Bit.Accent))
            if (c.contact.photoUri != null) {
                Avatar(listOf(c.contact), size * 0.62f)
            } else {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "#",
                    color = Bit.OnAccent, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.4f).sp,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
        val first = name.take(1)
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(fontWeight = FontWeight.Light)); append(first); pop()
                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)); append(name.drop(1)); pop()
            },
            fontFamily = Body, fontSize = if (compact) 30.sp else 46.sp, letterSpacing = (-1.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (c.contact.name != null) Phone.format(context, c.number) else (sim ?: ""),
                color = Bit.TextDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ShapeTile(icon: ImageVector, label: String, on: Boolean, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(80.dp).clip(shape).background(if (on) Bit.Accent else Bit.Tile.copy(alpha = 0.95f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Bit.OnAccent else Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(
            label.lowercase().replaceFirstChar { it.uppercase() },
            color = if (on) Bit.Accent else Color.White, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1,
        )
    }
}

private data class TileSpec(val label: String, val icon: ImageVector, val on: Boolean, val state: String?, val onClick: () -> Unit)

@Composable
private fun Tile(icon: ImageVector, label: String, state: String?, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val fg = if (on) Bit.OnAccent else Color.White
    Column(
        modifier.clip(shape)
            .then(if (on) Modifier.background(Bit.Accent) else Modifier.background(GlassFill).border(1.5.dp, GlassLine, shape))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(24.dp))
        Column {
            Text(label, style = mono(11f, color = fg, spacing = 1.1f), maxLines = 1)
            if (state != null) Text(state, style = mono(10f, color = fg.copy(alpha = 0.7f), spacing = 1f))
        }
    }
}

/** A little animated level meter under the timer (decorative; flat while muted or on hold). */
@Composable
private fun VoiceBars(active: Boolean) {
    val t = rememberInfiniteTransition(label = "bars")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p")
    val tau = (2 * Math.PI).toFloat()
    Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(16) { i ->
            val wave = if (active) 0.5f + 0.5f * sin(p * tau * 2 + i * 0.7f) * sin(p * tau + i * 1.3f) else 0f
            val h = 6f + 20f * kotlin.math.abs(wave)
            Box(Modifier.width(6.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(Bit.Accent.copy(alpha = 0.35f + 0.6f * (h / 26f))))
        }
    }
}
