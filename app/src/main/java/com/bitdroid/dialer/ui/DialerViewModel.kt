package com.bitdroid.dialer.ui

import android.app.Application
import android.app.role.RoleManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitdroid.dialer.data.CallEntry
import com.bitdroid.dialer.data.CallKind
import com.bitdroid.dialer.data.CallLogRepo
import com.bitdroid.dialer.data.Contacts
import com.bitdroid.dialer.data.Favorite
import com.bitdroid.dialer.data.Phone
import com.bitdroid.dialer.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { CALLS, KEYPAD, SETTINGS }

class DialerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    val stack = mutableStateListOf(Screen.CALLS)

    private val _isDefault = MutableStateFlow(checkDefault())
    val isDefault: StateFlow<Boolean> = _isDefault

    private val _recents = MutableStateFlow<List<CallEntry>>(emptyList())
    val recents: StateFlow<List<CallEntry>> = _recents
    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites
    private val _missed = MutableStateFlow(0)
    val missed: StateFlow<Int> = _missed

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _results = MutableStateFlow<List<Contacts.PhoneEntry>>(emptyList())
    val results: StateFlow<List<Contacts.PhoneEntry>> = _results

    /** Number typed on the keypad (kept so "Add call" and dial intents can prefill it). */
    val typed = MutableStateFlow("")

    private val _monet = MutableStateFlow(Prefs.monetBackground(app))
    val monet: StateFlow<Boolean> = _monet
    private val _listStyle = MutableStateFlow(Prefs.listStyle(app))
    val listStyle: StateFlow<Prefs.ListStyle> = _listStyle
    private val _dtmf = MutableStateFlow(Prefs.dtmfTones(app))
    val dtmf: StateFlow<Boolean> = _dtmf

    private var refreshJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                delay(250)
                refresh()
            }
        }
    }
    private var observing = false

    private fun checkDefault() =
        ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    fun onResume() {
        _isDefault.value = checkDefault()
        if (_isDefault.value && !observing) {
            try {
                ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
                observing = true
            } catch (_: Exception) {
            }
        }
        Contacts.clearCache()
        refresh()
    }

    override fun onCleared() {
        if (observing) ctx.contentResolver.unregisterContentObserver(observer)
    }

    fun refresh() {
        viewModelScope.launch {
            val (r, f, m) = withContext(Dispatchers.IO) {
                val recents = CallLogRepo.recents(ctx)
                val favs = Contacts.favorites(ctx).map { (c, n) ->
                    val missed = recents.filter { it.isNew && it.kind == CallKind.MISSED && Phone.same(it.number, n) }.sumOf { it.count }
                    Favorite(c, n, missed)
                }
                Triple(recents, favs, CallLogRepo.unseenMissed(ctx))
            }
            _recents.value = r
            _favorites.value = f
            _missed.value = m
        }
    }

    fun markMissedSeen() {
        viewModelScope.launch(Dispatchers.IO) { CallLogRepo.markMissedSeen(ctx) }
    }

    fun setQuery(q: String) {
        _query.value = q
        viewModelScope.launch {
            _results.value = if (q.isBlank()) emptyList() else withContext(Dispatchers.IO) { Contacts.search(ctx, q) }
        }
    }

    fun deleteFromHistory(number: String) {
        viewModelScope.launch(Dispatchers.IO) { CallLogRepo.delete(ctx, number) }
        _recents.value = _recents.value.filterNot { it.number == number }
    }

    fun push(s: Screen) {
        if (stack.last() != s) stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun setMonet(on: Boolean) { Prefs.setMonetBackground(ctx, on); _monet.value = on }
    fun setListStyle(s: Prefs.ListStyle) { Prefs.setListStyle(ctx, s); _listStyle.value = s }
    fun setDtmf(on: Boolean) { Prefs.setDtmfTones(ctx, on); _dtmf.value = on }
}
