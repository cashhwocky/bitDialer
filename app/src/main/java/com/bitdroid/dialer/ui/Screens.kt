package com.bitdroid.dialer.ui

import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.StarOutline
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.launch

fun call(context: android.content.Context, number: String) {
    if (number.isBlank()) return
    if (!CallManager.placeCall(context, number)) {
        Toast.makeText(context, "Couldn't start the call. Make sure bitDialer is your default phone app.", Toast.LENGTH_LONG).show()
        return
    }
    // We're in the foreground, so open the call screen directly instead of waiting on Telecom.
    openCallScreen(context)
}

fun openCallScreen(context: android.content.Context) {
    context.startActivity(
        Intent(context, com.bitdroid.dialer.call.InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
}

/** True when Android lets bitDialer show incoming calls full screen over the lock screen. */
fun canShowFullScreenCalls(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() == true

/** Warns when incoming calls can't show full screen, with a button straight to the setting. */
@Composable
fun FullScreenCallsWarning() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(canShowFullScreenCalls(context)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = canShowFullScreenCalls(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (allowed) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)).border(2.dp, Bit.Missed, RoundedCornerShape(24.dp)).padding(16.dp),
    ) {
        Text("INCOMING CALLS WON'T SHOW FULL SCREEN", style = mono(11f, color = Bit.Missed, spacing = 1.2f))
        Spacer(Modifier.height(6.dp))
        Text("Allow full-screen notifications so calls appear over your lock screen.", fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.height(44.dp).clip(CircleShape).background(Bit.Accent).clickable {
                val i = if (android.os.Build.VERSION.SDK_INT >= 34) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                }
                try {
                    context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Allow", color = Bit.OnAccent, fontWeight = FontWeight.Bold) }
    }
}

/** Shown at the top of the app while a call is live: tap to go back to it. */
@Composable
fun ReturnToCallBar() {
    val calls by CallManager.calls.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val p = CallManager.primary(calls)
    if (p == null || p.ended) return
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(CircleShape).background(Bit.Accent).clickable { openCallScreen(context) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Call, null, tint = Bit.OnAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            (p.contact.name ?: Phone.format(context, p.number)) + " · " + when {
                p.ringing -> "INCOMING"
                p.dialing -> "CALLING…"
                p.holding -> "ON HOLD"
                else -> "ON CALL"
            },
            style = mono(12f, color = Bit.OnAccent, spacing = 1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("RETURN", style = mono(11f, color = Bit.OnAccent, spacing = 1.2f))
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
                if (Bit.expressive) HomeCallsHeader(vm, recents.size, missed, favorites.size) else
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

            item(key = "fsi") { FullScreenCallsWarning() }
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
                    if (!Bit.expressive) item(key = "fh") { SectionRule("FAVORITES") }
                    item(key = "favs") { FavoriteGrid(favorites, onCall = { call(context, it.number) }, onUnpin = vm::unpin) }
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
                    if (!Bit.expressive) item(key = "rh2") { SectionRule("RECENT") } else item(key = "rgap") { Spacer(Modifier.height(10.dp)) }
                    itemsIndexed(recents, key = { _, e -> e.id }) { i, e ->
                        val surface = if (Bit.expressive) {
                            // Missed calls get their own pink pill; the rest share purple-gray tiles.
                            val pink = e.isMissed
                            if (pink) Modifier.padding(start = 14.dp, end = 14.dp, top = if (i > 0 && !recents[i - 1].isMissed) 10.dp else 0.dp, bottom = 10.dp).clip(CircleShape).background(Bit.Pink)
                            else homePanel(i == 0 || recents[i - 1].isMissed, i == recents.lastIndex || recents[i + 1].isMissed)
                        } else rowSurface(style, i == 0, i == recents.lastIndex)
                        Box(surface.then(Modifier.animateItem(placementSpec = Bit.placement))) {
                            RecentRow(e, vm)
                        }
                    }
                }
            }
        }

        val ex = Bit.expressive
        val fadeTo = if (ex) Color(0xFF0F1419) else Bit.Black
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, fadeTo.copy(alpha = 0.7f), fadeTo.copy(alpha = 0.85f)))),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                if (ex) {
                    Modifier.weight(1f).height(62.dp).clip(CircleShape).background(Color(0xFF463E60))
                        .border(2.5.dp, Bit.Accent.copy(alpha = 0.55f), CircleShape).padding(start = 7.dp, end = 6.dp)
                } else {
                    Modifier.weight(1f).height(62.dp).clip(CircleShape).background(Bit.Black.copy(alpha = 0.5f))
                        .border(2.5.dp, Color.White, CircleShape).padding(start = 20.dp, end = 6.dp)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ex) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Bit.Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Search, null, tint = Bit.OnAccent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                } else {
                    Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        if (ex) Text("Search contacts", color = Color(0xFFEDE6FF), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        else Text("Search contacts", color = Bit.TextDim, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
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
                Modifier.size(if (ex) 66.dp else 62.dp).clip(if (ex) Shapes.Squircle else CircleShape).background(if (ex) Bit.Light else Bit.Accent).clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    vm.push(Screen.KEYPAD)
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Dialpad, "Keypad", tint = Bit.OnAccent, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun FavoriteGrid(favs: List<Favorite>, onCall: (Favorite) -> Unit, onUnpin: (Favorite) -> Unit) {
    val per = if (Bit.expressive) 4 else 3
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(if (Bit.expressive) 14.dp else 22.dp)) {
        favs.chunked(per).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, f ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (Bit.expressive) HomeFavorite(f, favShapes[(r * per + i) % favShapes.size], onCall, onUnpin) else FavoriteItem(f, onCall, onUnpin)
                    }
                }
                repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteItem(f: Favorite, onCall: (Favorite) -> Unit, onUnpin: (Favorite) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val view = LocalView.current
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Bit.bouncy) }
    Column(
        Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = { onCall(f) }, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); menu = true })
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
        FavoriteMenu(f, menu, { menu = false }, onUnpin)
    }
}

/** Hold menu for a favorite: unpin, contact card, message. */
@Composable
private fun FavoriteMenu(f: Favorite, open: Boolean, onDismiss: () -> Unit, onUnpin: (Favorite) -> Unit) {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onUnpin(f)
        else android.widget.Toast.makeText(context, "bitDialer needs contacts access to unpin", android.widget.Toast.LENGTH_SHORT).show()
    }
    DropdownMenu(open, onDismiss, containerColor = Bit.SurfaceHigh) {
        DropdownMenuItem(
            text = { Text("Unpin") },
            leadingIcon = { Icon(Icons.Rounded.StarOutline, null) },
            onClick = {
                onDismiss()
                if (context.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) onUnpin(f)
                else ask.launch(android.Manifest.permission.WRITE_CONTACTS)
            },
        )
        f.contact.lookupUri?.let { uri ->
            DropdownMenuItem(
                text = { Text("Contact card") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { onDismiss(); context.startActivity(Intent(Intent.ACTION_VIEW, uri)) },
            )
        }
        DropdownMenuItem(
            text = { Text("Message") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, null) },
            onClick = { onDismiss(); context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", f.number, null))) },
        )
    }
}

private val favShapes = listOf(Shapes.Ripple, Shapes.Gem, Shapes.Clover, Shapes.Circle, Shapes.Sunny, Shapes.Squircle)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeFavorite(f: Favorite, shape: androidx.compose.ui.graphics.Shape, onCall: (Favorite) -> Unit, onUnpin: (Favorite) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val view = LocalView.current
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Bit.bouncy) }
    Column(
        Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(22.dp))
            .combinedClickable(onClick = { onCall(f) }, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); menu = true })
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Avatar(listOf(f.contact), 72.dp, shape = shape)
            if (f.missed > 0) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(Shapes.Sunny).background(Color(0xFFF28FB5)))
        }
        Spacer(Modifier.height(6.dp))
        Text(f.contact.name?.substringBefore(' ') ?: f.number, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 84.dp))
        FavoriteMenu(f, menu, { menu = false }, onUnpin)
    }
}

/** bitHome header: a title tile, a lavender missed-call pill and a pink favorites tile, like the home screen widgets. */
@Composable
private fun HomeCallsHeader(vm: DialerViewModel, recents: Int, missed: Int, favorites: Int) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val today = remember { java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date()) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp).height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.weight(1.3f).fillMaxSize().clip(RoundedCornerShape(40.dp, 14.dp, 40.dp, 40.dp)).background(Bit.Tile)
                .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Light)); append("Ca"); pop()
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)); append("lls"); pop()
                },
                fontFamily = Body, fontSize = 42.sp, letterSpacing = (-1.5).sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(bottom = 18.dp),
            )
            Text("$today · $recents recent", color = Bit.TextDim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart))
            Box(Modifier.align(Alignment.TopEnd)) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(CircleShape).background(Bit.Accent).padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(Shapes.Sunny).background(Bit.OnAccent), contentAlignment = Alignment.Center) {
                    Text(if (missed > 99) "99+" else "$missed", color = Bit.Accent, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (missed == 1) "Missed\ncall" else "Missed\ncalls", color = Bit.OnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 17.sp)
            }
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp, 30.dp, 30.dp, 30.dp)).background(Bit.Pink).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(Shapes.Heart).background(Bit.PinkText))
                Spacer(Modifier.width(10.dp))
                Text("$favorites favorites", color = Bit.PinkText, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 17.sp)
            }
        }
    }
}

private fun homePanel(first: Boolean, last: Boolean): Modifier {
    val shape = RoundedCornerShape(
        topStart = if (first) 34.dp else 0.dp, topEnd = if (first) 34.dp else 0.dp,
        bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 34.dp else 0.dp,
    )
    return Modifier.padding(horizontal = 14.dp).clip(shape).background(Bit.Tile)
        .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)
}

private val CallEntry.isMissed get() = kind == CallKind.MISSED || kind == CallKind.REJECTED

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
    val ex = Bit.expressive
    val pink = ex && missed
    val title = e.contact.name ?: Phone.format(context, e.number).ifBlank { "Unknown" }
    val meta = if (ex) buildString {
        append(
            when (e.kind) {
                CallKind.OUTGOING -> "Outgoing"
                CallKind.MISSED -> "Missed"
                CallKind.REJECTED -> "Declined"
                CallKind.BLOCKED -> "Blocked"
                CallKind.VOICEMAIL -> "Voicemail"
                CallKind.INCOMING -> "Incoming"
            },
        )
        if (e.count > 1) append(" · ${e.count}×")
        append(" · ${shortTime(context, e.date)}")
        if (!missed && e.durationSec > 0) append(" · ${duration(e.durationSec)}")
    } else buildString {
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
            Modifier.fillMaxWidth().clip(if (pink) CircleShape else RoundedCornerShape(24.dp))
                .combinedClickable(onClick = { call(context, e.number) }, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); menu = true })
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ex) {
                Avatar(listOf(e.contact), 50.dp, shape = if (pink) Shapes.Gem else Shapes.Circle)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (pink) Bit.PinkText else Color.White, fontSize = 17.sp, fontWeight = if (pink) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(kindIcon(e.kind), null, tint = if (pink) Bit.PinkText else Bit.TextDim, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(meta, color = if (pink) Bit.PinkText else Bit.TextDim, fontSize = 13.5.sp, fontWeight = if (pink) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(if (pink) 52.dp else 44.dp)
                        .clip(if (pink) Shapes.Clover else RoundedCornerShape(15.dp))
                        .background(if (pink) Bit.PinkText else Color.White.copy(alpha = 0.08f))
                        .clickable { call(context, e.number) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Call, "Call", tint = if (pink) Color(0xFF4A1E3E) else Bit.Accent, modifier = Modifier.size(20.dp)) }
            } else {
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
    val vibrate by vm.vibrate.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Text("Settings", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { SectionRule("LOOK") }
            item { ThemePicker() }
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
            item { IconPicker() }
            item { SectionRule("CALLS") }
            item {
                Toggle("Vibrate for incoming calls", "Off in Silent mode and Do Not Disturb", vibrate) {
                    vm.setVibrate(it); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
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
            item { SectionRule("CALL DIAGNOSTICS") }
            item { CallDiagnostics() }
            item { SectionRule("ABOUT") }
            item { Row2("bitDialer", "Phone app for bitDroid · v0.3.0") {} }
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

@Composable
private fun IconPicker() {
    val context = LocalContext.current
    val view = LocalView.current
    var current by remember { mutableStateOf(com.bitdroid.dialer.data.AppIcons.current(context)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text("App icon", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Your home screen may take a moment to update", color = Bit.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            com.bitdroid.dialer.data.AppIcons.all.forEach { c ->
                val on = c.key == current
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .border(if (on) 3.dp else 1.5.dp, if (on) Bit.Accent else Bit.GlassEdge, CircleShape)
                            .padding(5.dp).clip(CircleShape).background(Color(c.bg))
                            .clickable {
                                if (!on) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    com.bitdroid.dialer.data.AppIcons.set(context, c.key)
                                    current = c.key
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(
                                c.art ?: if (c.darkMark) com.bitdroid.dialer.R.drawable.ic_launcher_foreground_dark else com.bitdroid.dialer.R.drawable.ic_launcher_foreground,
                            ),
                            contentDescription = c.label,
                            modifier = Modifier.size(80.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(c.label, style = mono(10f, color = if (on) Bit.Accent else Bit.TextDim, spacing = 0.8f))
                }
            }
        }
    }
}

// ======================================================= call diagnostics

@Composable
private fun CallDiagnostics() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var countdown by remember { mutableStateOf(0) }
    val pkgUri = Uri.fromParts("package", context.packageName, null)

    val isDefault = remember(tick) {
        context.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER) == true
    }
    val notifs = remember(tick) { com.bitdroid.dialer.call.CallNotifier.canPost(context) }
    val fullScreen = remember(tick) { com.bitdroid.dialer.call.CallNotifier.fullScreenAllowed(context) }
    val channelOk = remember(tick) {
        val ch = context.getSystemService(android.app.NotificationManager::class.java)?.getNotificationChannel("incoming_calls")
        ch != null && ch.importance >= android.app.NotificationManager.IMPORTANCE_HIGH
    }
    val events = remember(tick, countdown) { com.bitdroid.dialer.call.Diag.read(context) }
    val dndOff = remember(tick, countdown) {
        context.getSystemService(android.app.NotificationManager::class.java)?.currentInterruptionFilter.let {
            it == null || it == android.app.NotificationManager.INTERRUPTION_FILTER_ALL || it == android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
    }

    fun open(i: Intent) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DiagRow(isDefault, "Default phone app", if (isDefault) "bitDialer handles your calls" else "Another app is handling calls", if (isDefault) null else "FIX") {
            open(Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))
        }
        DiagRow(notifs, "Notifications allowed", if (notifs) "Ready" else "Calls can't ring on screen without this", if (notifs) null else "ALLOW") {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        DiagRow(fullScreen, "Full-screen calls allowed", if (fullScreen) "Calls can show over the lock screen" else "Needed for the ring screen over the lock screen", if (fullScreen) null else "ALLOW") {
            open(
                if (android.os.Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri),
            )
        }
        DiagRow(channelOk, "Incoming calls notifications", if (channelOk) "On, high priority" else "Turned down or off — calls can't pop up", if (channelOk) null else "FIX") {
            open(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, "incoming_calls"),
            )
        }

        DiagRow(
            dndOff, "Do Not Disturb",
            if (dndOff) "Off" else "On — calls only pop up full screen for people you allow in Do Not Disturb",
            if (dndOff) null else "OPEN",
        ) { open(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)) }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
                .background(if (countdown > 0) Bit.SurfaceHigh else Bit.Accent)
                .clickable(enabled = countdown == 0) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    scope.launch {
                        for (i in 5 downTo 1) {
                            countdown = i
                            kotlinx.coroutines.delay(1000)
                        }
                        countdown = 0
                        com.bitdroid.dialer.call.CallNotifier.postTest(context)
                        tick++
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (countdown > 0) "Lock your phone now… $countdown" else "Test incoming call",
                color = if (countdown > 0) Color.White else Bit.OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }
        Text(
            "Tap it, then lock your phone. In 5 seconds the full-screen ring screen should appear. If only a notification shows, screenshot this page and send it.",
            color = Bit.TextDim, fontSize = 13.sp, lineHeight = 18.sp,
        )

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT CALL EVENTS", style = mono(11f, spacing = 1.4f), modifier = Modifier.weight(1f))
            Text(
                "CLEAR", style = mono(11f, color = Bit.Accent, spacing = 1.2f),
                modifier = Modifier.clip(CircleShape).clickable { com.bitdroid.dialer.call.Diag.clear(context); tick++ }.padding(8.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (events.isEmpty()) Text("Nothing yet. Make or get a call, then come back.", color = Bit.TextDim, fontSize = 13.sp)
            events.take(25).forEach { Text(it, style = mono(10.5f, FontWeight.Medium, color = Color.White, spacing = 0f)) }
        }
    }
}

@Composable
private fun DiagRow(ok: Boolean, title: String, sub: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (ok) Color.White.copy(alpha = 0.07f) else Bit.Missed, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (ok) Bit.Accent else Bit.Missed))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Bit.TextDim, fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.height(36.dp).clip(CircleShape).background(Bit.Accent).clickable(onClick = onAction).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, style = mono(11f, color = Bit.OnAccent, spacing = 1f)) }
        }
    }
}
