package io.github.nrupeshsurya.wisp.ui.stopwatch

import android.app.Application
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nrupeshsurya.wisp.R
import io.github.nrupeshsurya.wisp.app
import io.github.nrupeshsurya.wisp.ui.Format
import io.github.nrupeshsurya.wisp.ui.components.ScreenTopBar

/**
 * Elapsed time = accumulated + (elapsedRealtime - runningSince) while running. Persisted in prefs,
 * so the stopwatch keeps going if the app is killed, without a foreground service.
 */
class StopwatchViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.app.prefs
    private val notifications = application.app.notifications

    var runningSince by mutableLongStateOf(prefs.stopwatchRunningSince)
        private set
    var accumulated by mutableLongStateOf(prefs.stopwatchAccumulatedMs)
        private set
    var laps by mutableStateOf(prefs.stopwatchLaps)
        private set

    val isRunning get() = runningSince != 0L

    fun elapsed(nowRealtime: Long = SystemClock.elapsedRealtime()) =
        accumulated + if (isRunning) nowRealtime - runningSince else 0L

    fun startOrPause() {
        if (isRunning) {
            accumulated = elapsed()
            runningSince = 0L
            notifications.cancelStopwatch()
        } else {
            runningSince = SystemClock.elapsedRealtime()
            notifications.showStopwatch(System.currentTimeMillis() - accumulated)
        }
        save()
    }

    fun lap() {
        laps = laps + elapsed()
        save()
    }

    fun reset() {
        runningSince = 0L
        accumulated = 0L
        laps = emptyList()
        notifications.cancelStopwatch()
        save()
    }

    private fun save() {
        prefs.stopwatchRunningSince = runningSince
        prefs.stopwatchAccumulatedMs = accumulated
        prefs.stopwatchLaps = laps
    }
}

@Composable
fun StopwatchScreen(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val vm: StopwatchViewModel = viewModel()
    var frameNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // Redraw every frame only while running and visible.
    LaunchedEffect(vm.isRunning) {
        while (vm.isRunning) withFrameMillis { frameNow = SystemClock.elapsedRealtime() }
    }
    val elapsed = vm.elapsed(if (vm.isRunning) frameNow else SystemClock.elapsedRealtime())

    Scaffold(modifier = modifier, topBar = { ScreenTopBar("Stopwatch", onSettings) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Text(
                Format.duration(elapsed, centis = true),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.testTag("stopwatch_time"),
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                val canSecondary = vm.isRunning || elapsed > 0
                FilledTonalIconButton(
                    onClick = { if (vm.isRunning) vm.lap() else vm.reset() },
                    enabled = canSecondary,
                    modifier = Modifier.size(64.dp).testTag("stopwatch_secondary"),
                ) {
                    if (vm.isRunning) Icon(painterResource(R.drawable.ic_flag), contentDescription = "Lap")
                    else Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
                FilledIconButton(onClick = vm::startOrPause, modifier = Modifier.size(88.dp).testTag("stopwatch_start")) {
                    if (vm.isRunning) Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause", modifier = Modifier.size(36.dp))
                    else Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                val laps = vm.laps
                itemsIndexed(laps.asReversed()) { i, total ->
                    val n = laps.size - i
                    val previous = if (n >= 2) laps[n - 2] else 0L
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("Lap $n", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(Format.duration(total - previous, centis = true), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            Format.duration(total, centis = true),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
