package com.bitdroid.dialer.ui

import dev.chrisbanes.haze.hazeSource
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.dialer.data.CallEntry
import com.bitdroid.dialer.data.CallKind
import com.bitdroid.dialer.data.Contact
import com.bitdroid.dialer.data.Phone

/** Fruit calls screen: big title, favorites, recents with an All/Missed toggle, and a floating glass tab bar. */
@Composable
fun FruitCallsScreen(vm: DialerViewModel) {
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val missed by vm.missed.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var onlyMissed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { vm.markMissedSeen() } }
    val shown = if (onlyMissed) recents.filter { it.kind == CallKind.MISSED || it.kind == CallKind.REJECTED } else recents

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        // All / Missed toggle.
                        Row(Modifier.height(40.dp).glass(CircleShape).padding(4.dp)) {
                            listOf(false to "All", true to "Missed").forEach { (m, label) ->
                                val on = onlyMissed == m
                                Box(
                                    Modifier.fillMaxHeight().clip(CircleShape).background(if (on) Bit.Accent else Color.Transparent)
                                        .squish { onlyMissed = m }.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Bit.OnAccent else Color(0xFFE8E0FF)) }
                            }
                        }
                    }
                    Row(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Light)); append("Ca"); pop()
                                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold)); append("lls"); pop()
                            },
                            fontFamily = Body, fontSize = 38.sp, letterSpacing = (-1.2).sp,
                        )
                        if (missed > 0) {
                            Spacer(Modifier.width(10.dp))
                            Text("$missed MISSED", style = mono(12f, color = Bit.MissedPink, spacing = 0.5f), modifier = Modifier.padding(bottom = 7.dp))
                        }
                    }
                }
            }
            item(key = "fsi") { FullScreenCallsWarning() }
            if (query.isNotBlank()) {
                items(results, key = { "r" + it.name + it.number }) { r ->
                    FruitRow(Contact(r.number, r.name, r.photo, null), r.name, "${r.label.uppercase()} · ${Phone.format(context, r.number)}", null, false, onCall = { call(context, r.number) }, onLong = {})
                }
                if (results.isEmpty() && query.any(Char::isDigit)) {
                    item(key = "dial") { FruitRow(Contact(query, null, null, null), query, "CALL THIS NUMBER", null, false, onCall = { call(context, query) }, onLong = {}) }
                }
            } else {
                if (favorites.isNotEmpty() && !onlyMissed) {
                    item(key = "favs") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp)).background(Bit.Surface.copy(alpha = 0.85f))
                                .padding(vertical = 14.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            favorites.take(4).forEachIndexed { i, f -> FruitFavorite(f, favShapesFruit[i % favShapesFruit.size], onCall = { call(context, f.number) }, onUnpin = vm::unpin) }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (onlyMissed) "No missed calls." else "No calls yet. Tap Keypad to make one.",
                            color = Bit.TextDim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 40.dp),
                        )
                    }
                } else {
                    itemsIndexed(shown, key = { _, e -> e.id }) { i, e ->
                        fun miss(x: CallEntry) = x.kind == CallKind.MISSED || x.kind == CallKind.REJECTED
                        if (miss(e)) {
                            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = if (i > 0 && !miss(shown[i - 1])) 10.dp else 0.dp, bottom = 10.dp).clip(CircleShape).background(Bit.Pink)) {
                                FruitRecent(e, vm)
                            }
                            return@itemsIndexed
                        }
                        val first = i == 0 || miss(shown[i - 1])
                        val last = i == shown.lastIndex || miss(shown[i + 1])
                        val shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = if (first) 30.dp else 0.dp, topEnd = if (first) 30.dp else 0.dp,
                            bottomStart = if (last) 30.dp else 0.dp, bottomEnd = if (last) 30.dp else 0.dp,
                        )
                        Box(Modifier.padding(horizontal = 14.dp).clip(shape).background(Bit.Tile).padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)) {
                            FruitRecent(e, vm)
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FruitTab(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.weight(1f).squish(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp, 34.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Color.White else Color(0xFFA0A0AA), modifier = Modifier.size(21.dp)) }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Color(0xFFA0A0AA))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FruitFavorite(f: com.bitdroid.dialer.data.Favorite, shape: androidx.compose.ui.graphics.Shape, onCall: () -> Unit, onUnpin: (com.bitdroid.dialer.data.Favorite) -> Unit) {
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onCall, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); menu = true })
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Avatar(listOf(f.contact), 70.dp, shape = shape)
            if (f.missed > 0) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(Shapes.Sunny).background(Color(0xFFF28FB5)))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            f.contact.name?.substringBefore(' ') ?: f.number, fontSize = 12.5.sp, color = Bit.TextDim,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 80.dp),
        )
        FavoriteMenu(f, menu, { menu = false }, onUnpin)
    }
}

private val favShapesFruit = listOf(Shapes.Ripple, Shapes.Clover, Shapes.Circle, Shapes.Ripple7)

@Composable
private fun FruitRecent(e: CallEntry, vm: DialerViewModel) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val missed = e.kind == CallKind.MISSED || e.kind == CallKind.REJECTED
    val title = e.contact.name ?: Phone.format(context, e.number).ifBlank { "Unknown" }
    val sub = buildString {
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
        FruitRow(e.contact, title, sub, shortTime(context, e.date).uppercase(), missed, onCall = { call(context, e.number) }, onLong = { menu = true })
        DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
            DropdownMenuItem(text = { Text("Send message") }, onClick = {
                menu = false
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", e.number, null)))
            })
            e.contact.lookupUri?.let { uri ->
                DropdownMenuItem(text = { Text("Contact card") }, onClick = { menu = false; context.startActivity(Intent(Intent.ACTION_VIEW, uri)) })
            }
            DropdownMenuItem(text = { Text("Delete from history", color = Bit.Error) }, onClick = { menu = false; vm.deleteFromHistory(e.number) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FruitRow(contact: Contact, title: String, sub: String, time: String?, missed: Boolean, onCall: () -> Unit, onLong: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onCall, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onLong() })
            .padding(start = 18.dp).height(70.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(listOf(contact), 48.dp, shape = if (missed) Shapes.Ripple7 else Shapes.Circle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.weight(1f).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, color = if (missed) Bit.MissedPink else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, style = mono(11f, FontWeight.Medium, if (missed) Bit.MissedPink else Bit.TextDim, 0.3f), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
                if (time != null) {
                    Text(time, style = mono(11f, FontWeight.Medium, Bit.TextDim, 0.3f), modifier = Modifier.padding(horizontal = 10.dp))
                }
                Box(Modifier.size(40.dp).squish(onClick = onCall).clip(Shapes.Clover).background(if (missed) Bit.PinkText else Bit.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Call, "Call", tint = Bit.OnAccent, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}
