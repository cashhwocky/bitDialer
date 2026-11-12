package com.bitdroid.dialer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.dialer.call.CallManager
import com.bitdroid.dialer.data.CallEntry
import com.bitdroid.dialer.data.CallKind
import com.bitdroid.dialer.data.Contact
import com.bitdroid.dialer.data.Contacts
import com.bitdroid.dialer.data.Favorite
import com.bitdroid.dialer.data.Phone
import com.bitdroid.dialer.data.Prefs

fun call(context: android.content.Context, number: String) {
    if (number.isBlank()) return
    if (!CallManager.placeCall(context, number)) {
        Toast.makeText(context, "Set bitDialer as your phone app to make calls", Toast.LENGTH_SHORT).show()
    }
}

// ================================================================= calls

@Composable
fun CallsScreen(vm: DialerViewModel) {
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val missed by vm.missed.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val style by vm.listStyle.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { vm.markMissedSeen() } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToggleMark()
                        Spacer(Modifier.width(10.dp))
                        Text("BITDIALER", style = mono(12f, color = Bit.Accent, spacing = 1.6f), modifier = Modifier.weight(1f))
                        Box {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).border(2.dp, Bit.GlassEdge, CircleShape).clickable { menu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(16.dp, 2.5.dp).clip(CircleShape).background(Color.White))
                                    Box(Modifier.size(10.dp, 2.5.dp).clip(CircleShape).background(Color.White))
                                }
                            }
                            DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; vm.push(Screen.SETTINGS) })
                                DropdownMenuItem(text = { Text("Call settings") }, onClick = {
                                    menu = false
                                    try {
                                        context.startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                })
                            }
                        }
                    }
                    Text(
                        "Calls", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = (-1.5).sp,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp),
                    )
                    val status = buildAnnotatedString {
                        if (missed > 0) {
                            pushStyle(SpanStyle(color = Bit.Missed))
                            append("$missed MISSED")
                            pop()
                            append(" · ")
                        }
                        append("${recents.size} RECENT")
                    }
                    Text(status, style = mono(12.5f, FontWeight.Medium, spacing = 0.6f), modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 4.dp))
                }
            }

            if (query.isNotBlank()) {
                item(key = "rh") { SectionRule("CONTACTS") }
                items(results, key = { "r" + it.name + it.number }) { r ->
                    ContactRow(Contact(r.number, r.name, r.photo, null), "${r.label.uppercase()} · ${Phone.format(context, r.number)}") { call(context, r.number) }
                }
                if (results.isEmpty() && query.any(Char::isDigit)) {
                    item(key = "dial") {
                        ContactRow(Contact(query, null, null, null), "CALL THIS NUMBER") { call(context, query) }
                    }
                }
            } else {
                if (favorites.isNotEmpty()) {
                    item(key = "fh") { SectionRule("FAVORITES") }
                    item(key = "favs") { FavoriteGrid(favorites, onCall = { call(context, it.number) }, onOpen = { f ->
                        f.contact.lookupUri?.let { context.startActivity(Intent(Intent.ACTION_VIEW, it)) }
                    }) }
                }
                if (recents.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 50.dp)) {
                            Text("No calls\nyet", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 42.sp, color = Bit.Outline)
                            Spacer(Modifier.height(12.dp))
                            Text("Tap the keypad to make a call.", color = Bit.TextDim, fontSize = 15.sp)
                        }
                    }
                } else {
                    item(key = "rh2") { SectionRule("RECENT") }
                    itemsIndexed(recents, key = { _, e -> e.id }) { i, e ->
                        Box(rowSurface(style, i == 0, i == recents.lastIndex).then(Modifier.animateItem(placementSpec = Bit.placement))) {
                            RecentRow(e, vm)
                        }
                    }
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Bit.Black.copy(alpha = 0.7f), Bit.Black.copy(alpha = 0.85f)))),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.weight(1f).height(62.dp).clip(CircleShape).background(Bit.Black.copy(alpha = 0.5f))
                    .border(2.5.dp, Color.White, CircleShape).padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search contacts", color = Bit.TextDim, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = query, onValueChange = vm::setQuery, singleLine = true,
                        textStyle = TextStyle(fontFamily = Body, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Bit.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear", tint = Color.White) }
            }
            Box(
                Modifier.size(62.dp).clip(CircleShape).background(Bit.Accent).clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    vm.push(Screen.KEYPAD)
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Dialpad, "Keypad", tint = Bit.OnAccent, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun FavoriteGrid(favs: List<Favorite>, onCall: (Favorite) -> Unit, onOpen: (Favorite) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        favs.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { f -> Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { FavoriteItem(f, onCall, onOpen) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteItem(f: Favorite, onCall: (Favorite) -> Unit, onOpen: (Favorite) -> Unit) {
    val view = LocalView.current
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Bit.bouncy) }
    Column(
        Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = { onCall(f) }, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onOpen(f) })
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(if (f.missed > 0) Bit.Missed else Color.White.copy(alpha = 0.08f)))
            Avatar(listOf(f.contact), 90.dp, Modifier.border(3.dp, Bit.Black, CircleShape))
            if (f.missed > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-4).dp).clip(CircleShape).background(Bit.Black).padding(3.dp)
                        .clip(CircleShape).background(Bit.Missed).padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text("${f.missed}", style = mono(11f, color = Color(0xFF3A0B07), spacing = 0f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(f.contact.name?.substringBefore(' ') ?: f.number, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 108.dp))
    }
}

private fun kindIcon(k: CallKind): ImageVector = when (k) {
    CallKind.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade
    CallKind.MISSED, CallKind.REJECTED -> Icons.AutoMirrored.Rounded.CallMissed
    CallKind.BLOCKED -> Icons.Rounded.Block
    CallKind.VOICEMAIL -> Icons.Rounded.Voicemail
    CallKind.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived
}

private fun duration(sec: Long) = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec / 60) % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(e: CallEntry, vm: DialerViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    val missed = e.kind == CallKind.MISSED || e.kind == CallKind.REJECTED
    val title = e.contact.name ?: Phone.format(context, e.number).ifBlank { "Unknown" }
    val meta = buildString {
        append(
            when (e.kind) {
                CallKind.OUTGOING -> "OUTGOING"
                CallKind.MISSED -> "MISSED"
                CallKind.REJECTED -> "DECLINED"
                CallKind.BLOCKED -> "BLOCKED"
                CallKind.VOICEMAIL -> "VOICEMAIL"
                CallKind.INCOMING -> "INCOMING"
            },
        )
        if (e.count > 1) append(" · ${e.count}×")
        if (!missed && e.durationSec > 0) append(" · ${duration(e.durationSec)}")
    }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .combinedClickable(onClick = { call(context, e.number) }, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); menu = true })
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(listOf(e.contact), 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = if (missed) Bit.Missed else Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(shortTime(context, e.date).uppercase(), style = mono(11f, FontWeight.Medium, spacing = 0.4f))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(kindIcon(e.kind), null, tint = if (missed) Bit.Missed else Bit.TextDim, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(meta, style = mono(11f, color = if (missed) Bit.Missed else Bit.TextDim, spacing = 0.5f))
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).border(2.dp, Bit.GlassEdge, CircleShape).clickable { call(context, e.number) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Call, "Call", tint = Bit.Accent, modifier = Modifier.size(19.dp)) }
        }
        DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
            DropdownMenuItem(text = { Text("Send message") }, onClick = {
                menu = false
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", e.number, null)))
            })
            DropdownMenuItem(text = { Text("Copy number") }, onClick = {
                menu = false
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("number", e.number))
            })
            e.contact.lookupUri?.let { u ->
                DropdownMenuItem(text = { Text("View contact") }, onClick = { menu = false; context.startActivity(Intent(Intent.ACTION_VIEW, u)) })
            }
            DropdownMenuItem(text = { Text("Delete from history", color = Bit.Error) }, onClick = { menu = false; vm.deleteFromHistory(e.number) })
        }
    }
}

@Composable
private fun ContactRow(c: Contact, meta: String, onCall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onCall).padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(listOf(c), 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name ?: c.address, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = mono(11f, FontWeight.Medium, spacing = 0.4f))
        }
        Icon(Icons.Rounded.Call, "Call", tint = Bit.Accent)
    }
}

// ================================================================ keypad

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(vm: DialerViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val number by vm.typed.collectAsStateWithLifecycle()
    val tones by vm.dtmf.collectAsStateWithLifecycle()
    val toneGen = remember { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { toneGen?.release() } }
    val match = remember(number) { if (number.length >= 3) Contacts.lookup(context, number).takeIf { it.name != null } else null }
    val country = context.resources.configuration.locales[0].country.ifEmpty { "US" }
    val shown = remember(number) { PhoneNumberUtils.formatNumber(number, country) ?: number }

    fun press(d: Char) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (tones) {
            val tone = when (d) {
                in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (d - '0')
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> -1
            }
            if (tone >= 0) toneGen?.startTone(tone, 120)
        }
        vm.typed.value = number + d
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            ToggleMark()
            Spacer(Modifier.width(10.dp))
            Text("KEYPAD", style = mono(12f, color = Bit.Accent, spacing = 1.6f))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            shown.ifEmpty { " " }, fontFamily = Display, fontWeight = FontWeight.ExtraBold,
            fontSize = if (shown.length > 14) 28.sp else 38.sp, letterSpacing = (-1).sp, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            if (match != null) {
                Row(
                    Modifier.clip(CircleShape).border(2.dp, Bit.GlassEdge, CircleShape).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(listOf(match), 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(match.name ?: "", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        val keys = listOf(
            listOf('1' to "", '2' to "ABC", '3' to "DEF"),
            listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
            listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
            listOf('*' to "", '0' to "+", '#' to ""),
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    row.forEach { (d, letters) ->
                        Column(
                            Modifier.size(84.dp).clip(CircleShape).background(Bit.Glass).border(1.5.dp, Bit.GlassEdge, CircleShape)
                                .combinedClickable(
                                    onClick = { press(d) },
                                    onLongClick = when (d) {
                                        '0' -> ({ view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); vm.typed.value = number + '+' })
                                        '1' -> ({ view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); call(context, "voicemail:") })
                                        else -> null
                                    },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("$d", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
                            Text(letters, style = mono(10f, spacing = 1.4f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Spacer(Modifier.size(56.dp))
            Box(
                Modifier.size(86.dp).clip(CircleShape).background(Bit.Accent).clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    if (number.isNotBlank()) {
                        call(context, number)
                        vm.typed.value = ""
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Call, "Call", tint = Bit.OnAccent, modifier = Modifier.size(34.dp)) }
            Box(
                Modifier.size(56.dp).clip(CircleShape).combinedClickable(
                    onClick = { if (number.isNotEmpty()) vm.typed.value = number.dropLast(1) },
                    onLongClick = { vm.typed.value = "" },
                ),
                contentAlignment = Alignment.Center,
            ) { if (number.isNotEmpty()) Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = Bit.TextDim, modifier = Modifier.size(26.dp)) }
        }
        Spacer(Modifier.height(26.dp))
    }
}

// ============================================================== settings

@Composable
fun SettingsScreen(vm: DialerViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val monet by vm.monet.collectAsStateWithLifecycle()
    val style by vm.listStyle.collectAsStateWithLifecycle()
    val dtmf by vm.dtmf.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Text("Settings", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { SectionRule("LOOK") }
            item {
                Toggle("Monet background", "A glow in your wallpaper colors behind the black", monet) {
                    vm.setMonet(it); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("Recent calls style", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Prefs.ListStyle.CARDS to "GLASS CARDS", Prefs.ListStyle.PANEL to "FROSTED PANEL").forEach { (opt, label) ->
                            val on = opt == style
                            Box(
                                Modifier.height(40.dp).clip(CircleShape)
                                    .then(if (on) Modifier.background(Bit.Accent) else Modifier.border(2.dp, Bit.GlassEdge, CircleShape))
                                    .clickable { vm.setListStyle(opt); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = mono(11.5f, color = if (on) Bit.OnAccent else Color.White, spacing = 0.8f)) }
                        }
                    }
                }
            }
            item { SectionRule("CALLS") }
            item { Toggle("Keypad tones", "Play a tone when you tap the keypad", dtmf) { vm.setDtmf(it) } }
            item {
                Row2("Calling accounts & SIMs", "Carrier and SIM call settings") {
                    try {
                        context.startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
                    } catch (_: Exception) {
                        context.startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
                    }
                }
            }
            item {
                Row2("Blocked numbers", "Shared with bitMessages") {
                    try {
                        context.startActivity(context.getSystemService(TelecomManager::class.java)!!.createManageBlockedNumbersIntent())
                    } catch (_: Exception) {
                    }
                }
            }
            item { SectionRule("ABOUT") }
            item { Row2("bitDialer", "Phone app for bitDroid · v0.1.0") {} }
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Bit.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bit.OnAccent, checkedTrackColor = Bit.Accent,
                uncheckedThumbColor = Bit.TextDim, uncheckedTrackColor = Bit.SurfaceHigh, uncheckedBorderColor = Bit.TextDim,
            ),
        )
    }
}

@Composable
private fun Row2(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = Bit.TextDim, fontSize = 14.sp)
    }
}

// ================================================================= setup

@Composable
fun SetupScreen(onSetDefault: () -> Unit, refused: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Bit.bouncy) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(36.dp)).background(Bit.Accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Call, null, tint = Bit.OnAccent, modifier = Modifier.size(54.dp)) }
        Spacer(Modifier.height(32.dp))
        Text("bitDialer", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 36.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Set bitDialer as your default phone app to make and answer calls and see your call history.",
            color = Bit.TextDim, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.fillMaxWidth().height(58.dp).clip(CircleShape).background(Bit.Accent).clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onSetDefault()
            },
            contentAlignment = Alignment.Center,
        ) { Text("Set as default", color = Bit.OnAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (refused) {
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(2.dp, Bit.GlassEdge, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Text("DIDN'T WORK?", style = mono(11.5f, color = Bit.Accent, spacing = 1.4f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "If Android said the app was denied access, apps installed from an APK file need one extra step:\n\n1. Open App info below\n2. Tap ⋮ in the top corner → Allow restricted settings\n3. Come back and tap Set as default",
                    fontSize = 14.5.sp, lineHeight = 21.sp,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).border(2.5.dp, Color.White, CircleShape).clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("Open App info", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}
