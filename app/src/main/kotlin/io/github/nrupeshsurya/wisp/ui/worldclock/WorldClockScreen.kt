package io.github.nrupeshsurya.wisp.ui.worldclock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import io.github.nrupeshsurya.wisp.app
import io.github.nrupeshsurya.wisp.ui.Format
import io.github.nrupeshsurya.wisp.ui.components.ScreenTopBar
import io.github.nrupeshsurya.wisp.ui.components.SwipeToDelete
import io.github.nrupeshsurya.wisp.ui.components.rememberNow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun WorldClockScreen(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = context.app.prefs
    var zones by remember { mutableStateOf(prefs.worldClockZones) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow(1_000L)
    val local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())

    fun update(new: List<String>) { zones = new; prefs.worldClockZones = new }

    Scaffold(
        modifier = modifier,
        topBar = { ScreenTopBar("Clock", onSettings) },
        floatingActionButton = { FloatingActionButton(onClick = { picking = true }) { Icon(Icons.Default.Add, contentDescription = "Add city") } },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (t, suffix) = Format.timeParts(context, local.hour, local.minute)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(t, style = MaterialTheme.typography.displayLarge)
                        if (suffix.isNotEmpty()) Text(" $suffix", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(local.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(zones, key = { it }) { id ->
                SwipeToDelete(onDelete = { update(zones - id) }, modifier = Modifier.animateItem()) {
                    val there = Instant.ofEpochMilli(now).atZone(ZoneId.of(id))
                    val diffMin = (there.offset.totalSeconds - local.offset.totalSeconds) / 60
                    val dayDiff = there.toLocalDate().compareTo(local.toLocalDate())
                    val dayText = when { dayDiff > 0 -> "Tomorrow"; dayDiff < 0 -> "Yesterday"; else -> "Today" }
                    val diffText = if (diffMin == 0) "same time" else {
                        val sign = if (diffMin > 0) "+" else "−"
                        val h = abs(diffMin) / 60
                        val m = abs(diffMin) % 60
                        sign + (if (m == 0) "$h h" else "$h:%02d h".format(m))
                    }
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(cityName(id), style = MaterialTheme.typography.titleMedium)
                                Text("$dayText, $diffText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Format.time(context, there.hour, there.minute), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }

    if (picking) ZonePicker(exclude = zones.toSet(), onPick = { update(zones + it); picking = false }, onDismiss = { picking = false })
}

private val allZones: List<String> by lazy {
    ZoneId.getAvailableZoneIds()
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
        .sortedBy { cityName(it) }
}

private fun cityName(id: String) = id.substringAfterLast('/').replace('_', ' ')

private fun region(id: String) = id.substringBeforeLast('/').replace('_', ' ').replace("/", " · ")

@Composable
private fun ZonePicker(exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) {
        allZones.filter { it !in exclude && (query.isBlank() || it.replace('_', ' ').contains(query.trim(), ignoreCase = true)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add city") },
        text = {
            Column(Modifier.semantics { testTagsAsResourceId = true }) {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search, e.g. Tokyo") }, singleLine = true)
                LazyColumn(Modifier.height(360.dp)) {
                    items(matches, key = { it }) { id ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 12.dp).testTag("zone_$id")) {
                            Text(cityName(id), style = MaterialTheme.typography.bodyLarge)
                            Text(region(id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}
