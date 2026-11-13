package com.bitdroid.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.dialer.ui.Bit
import com.bitdroid.dialer.ui.BitTheme
import com.bitdroid.dialer.ui.CallsScreen
import com.bitdroid.dialer.ui.DialerViewModel
import com.bitdroid.dialer.ui.KeypadScreen
import com.bitdroid.dialer.ui.Screen
import com.bitdroid.dialer.ui.SettingsScreen
import com.bitdroid.dialer.ui.SetupScreen
import com.bitdroid.dialer.ui.monetGlow
import com.bitdroid.dialer.ui.backdrop
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    private val vm: DialerViewModel by viewModels()
    private val roleRefused = mutableStateOf(false)
    private var askedPerms = false

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onResume()
        roleRefused.value = !vm.isDefault.value
        if (vm.isDefault.value) requestPermissions()
    }
    private val permsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onResume() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Bit.apply(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            BitTheme {
                val isDefault by vm.isDefault.collectAsStateWithLifecycle()
                val monet by vm.monet.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().backdrop().monetGlow(monet && !Bit.expressive)) {
                    if (!isDefault) {
                        SetupScreen(onSetDefault = ::requestRole, refused = roleRefused.value)
                    } else {
                        var progress by remember { mutableFloatStateOf(0f) }
                        var fromLeft by remember { mutableStateOf(true) }
                        PredictiveBackHandler(enabled = vm.stack.size > 1) { events ->
                            try {
                                events.collect { e ->
                                    progress = e.progress
                                    fromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                                }
                                progress = 0f
                                vm.back()
                            } catch (e: CancellationException) {
                                progress = 0f
                                throw e
                            }
                        }
                        val target = vm.stack.last()
                        val depth = vm.stack.size
                        AnimatedContent(
                            targetState = target to depth,
                            modifier = Modifier.graphicsLayer {
                                val p = progress
                                scaleX = 1f - 0.1f * p
                                scaleY = 1f - 0.1f * p
                                translationX = (if (fromLeft) 1f else -1f) * p * 32.dp.toPx()
                                shape = RoundedCornerShape((36 * p).dp)
                                clip = p > 0f
                            },
                            transitionSpec = {
                                val forward = targetState.second >= initialState.second
                                val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                                val fade = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                if (forward) (slideInHorizontally(slide) { it / 3 } + fadeIn(fade)) togetherWith (slideOutHorizontally(slide) { -it / 6 } + fadeOut(fade))
                                else (slideInHorizontally(slide) { -it / 6 } + fadeIn(fade)) togetherWith (slideOutHorizontally(slide) { it / 3 } + fadeOut(fade))
                            },
                            label = "nav",
                        ) { (screen, _) ->
                            when (screen) {
                                Screen.CALLS -> CallsScreen(vm)
                                Screen.KEYPAD -> KeypadScreen(vm)
                                Screen.SETTINGS -> SettingsScreen(vm)
                            }
                        }
                        androidx.compose.foundation.layout.Box(Modifier.align(androidx.compose.ui.Alignment.TopCenter)) {
                            com.bitdroid.dialer.ui.ReturnToCallBar()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Bit.apply(this) // picks up wallpaper color changes for Material You
        vm.onResume()
        if (vm.isDefault.value) requestPermissions()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, "android.intent.action.CALL_BUTTON" -> {
                val data = intent.data
                if (data != null && data.scheme == "tel") vm.typed.value = data.schemeSpecificPart.orEmpty()
                if (intent.action != "android.intent.action.CALL_BUTTON" || data != null) vm.push(Screen.KEYPAD)
            }
        }
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java) ?: return
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    private fun requestPermissions() {
        if (askedPerms) return
        val wanted = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) {
            askedPerms = true
            permsLauncher.launch(wanted.toTypedArray())
        }
    }
}
