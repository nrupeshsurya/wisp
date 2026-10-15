package io.github.nrupeshsurya.wisp.ui.timer

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nrupeshsurya.wisp.R
import io.github.nrupeshsurya.wisp.app
import io.github.nrupeshsurya.wisp.data.Timer
import io.github.nrupeshsurya.wisp.data.TimerState
import io.github.nrupeshsurya.wisp.ui.Format
import io.github.nrupeshsurya.wisp.ui.components.ScreenTopBar
import io.github.nrupeshsurya.wisp.ui.components.SwipeToDelete
import io.github.nrupeshsurya.wisp.ui.components.rememberNow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.timers
    val timers = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(durationMs: Long, label: String) = viewModelScope.launch { repo.createAndStart(durationMs, label) }
    fun startOrPause(t: Timer) = viewModelScope.launch { if (t.state == TimerState.RUNNING) repo.pause(t) else repo.start(t) }
    fun reset(t: Timer) = viewModelScope.launch { repo.reset(t) }
    fun addMinute(t: Timer) = viewModelScope.launch { repo.addMinute(t) }
    fun delete(t: Timer) = viewModelScope.launch { repo.delete(t) }
}

@Composable
fun TimerScreen(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val vm: TimerViewModel = viewModel()
    val timers by vm.timers.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    val list = timers ?: return
    val showKeypad = adding || list.isEmpty()

    Scaffold(
        modifier = modifier,
        topBar = { ScreenTopBar("Timer", onSettings) },
        floatingActionButton = {
            if (!showKeypad) {
                FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "New timer") }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (showKeypad) {
            NewTimer(
                canCancel = list.isNotEmpty(),
                onCancel = { adding = false },
                onStart = { ms, label -> vm.create(ms, label); adding = false },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.id }) { t ->
                    SwipeToDelete(onDelete = { vm.delete(t) }, modifier = Modifier.animateItem()) { TimerCard(t, vm) }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(t: Timer, vm: TimerViewModel) {
    val now = rememberNow(if (t.state == TimerState.RUNNING) 200L else 60_000L)
    val remaining = t.remainingAt(now)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.label.ifBlank { Format.duration(t.durationMs) + " timer" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.delete(t) }) { Icon(Icons.Default.Close, contentDescription = "Delete timer") }
            }
            // Round up so a running timer never shows 0:00 before it actually rings.
            Text(
                Format.duration(remaining + 999),
                style = MaterialTheme.typography.displayMedium,
                color = if (t.state == TimerState.PAUSED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("timer_remaining"),
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (t.durationMs == 0L) 0f else (remaining.toFloat() / t.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.addMinute(t) }) { Text("+1:00") }
                if (t.state != TimerState.IDLE) {
                    IconButton(onClick = { vm.reset(t) }) { Icon(Icons.Default.Refresh, contentDescription = "Reset") }
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = { vm.startOrPause(t) }, modifier = Modifier.height(56.dp).width(72.dp).testTag("timer_play_pause")) {
                    if (t.state == TimerState.RUNNING) Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause")
                    else Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                }
            }
        }
    }
}

/** Google-Clock-style entry: digits fill from the right, so typing 1-3-0 gives 1:30. */
@Composable
private fun NewTimer(canCancel: Boolean, onCancel: () -> Unit, onStart: (Long, String) -> Unit, modifier: Modifier = Modifier) {
    var digits by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val padded = digits.padStart(6, '0')
    val h = padded.substring(0, 2).toInt()
    val m = padded.substring(2, 4).toInt()
    val s = padded.substring(4, 6).toInt()
    val totalMs = ((h * 3600L) + (m * 60L) + s) * 1000L

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "%02dh %02dm %02ds".format(h, m, s),
            style = MaterialTheme.typography.displayMedium,
            color = if (digits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp).testTag("timer_entry"),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 5, 10, 15, 30).forEach { min ->
                AssistChip(onClick = { onStart(min * 60_000L, label.trim()) }, label = { Text("$min min") })
            }
        }
        Spacer(Modifier.height(8.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    TextButton(
                        onClick = {
                            digits = when (key) {
                                "⌫" -> digits.dropLast(1)
                                else -> (digits + key).trimStart('0').take(6)
                            }
                        },
                        modifier = Modifier.weight(1f).aspectRatio(1.8f).testTag("key_$key"),
                    ) { Text(key, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) }
                }
            }
        }
        OutlinedTextField(
            value = label, onValueChange = { label = it }, label = { Text("Label (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canCancel) TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onStart(totalMs, label.trim()) }, enabled = totalMs > 0, modifier = Modifier.testTag("timer_start")) {
                Text("Start")
            }
        }
    }
}
