package io.github.nrupeshsurya.wisp.ui.alarms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nrupeshsurya.wisp.alarm.AlarmRepository
import io.github.nrupeshsurya.wisp.alarm.NextTrigger
import io.github.nrupeshsurya.wisp.app
import io.github.nrupeshsurya.wisp.data.Alarm
import io.github.nrupeshsurya.wisp.data.EndReason
import io.github.nrupeshsurya.wisp.quickadd.QuickAddResult
import io.github.nrupeshsurya.wisp.quickadd.QuickAlarm
import io.github.nrupeshsurya.wisp.ui.Format
import io.github.nrupeshsurya.wisp.ui.components.ScreenTopBar
import io.github.nrupeshsurya.wisp.ui.components.SectionHeader
import io.github.nrupeshsurya.wisp.ui.components.SwipeToDelete
import io.github.nrupeshsurya.wisp.ui.components.rememberNow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@Composable
fun AlarmsScreen(snackbar: SnackbarHostState, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val vm: AlarmsViewModel = viewModel()
    val sections by vm.sections.collectAsState()
    val now = rememberNow(15_000L)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var endedExpanded by rememberSaveable { mutableStateOf(true) }

    fun toast(message: String) = scope.launch { snackbar.showSnackbar(message) }
    fun deleteWithUndo(alarm: Alarm) {
        vm.delete(alarm)
        scope.launch {
            val r = snackbar.showSnackbar("Alarm deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete(alarm)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { ScreenTopBar("Alarms", onSettings) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = newAlarm() }, modifier = Modifier.testTag("add_alarm")) {
                Icon(Icons.Default.Add, contentDescription = "New alarm")
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { NextAlarmCard(sections.next, now) }
            item { PermissionWarnings() }
            item { QuickAddField(vm, now, onSaved = ::toast) }

            if (sections.loaded && sections.isEmpty) {
                item {
                    Text(
                        "No alarms yet.\nType something like “7:15 gym” or “nap 20m” above, or tap +.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            alarmSection(
                title = "Temporary · removed after they ring", alarms = sections.temporary, now = now,
                onClick = { editing = it }, onToggle = { a, on -> vm.setEnabled(a, on, ::toast) }, onDelete = ::deleteWithUndo,
            )
            alarmSection(
                title = "Alarms", alarms = sections.regular, now = now,
                onClick = { editing = it }, onToggle = { a, on -> vm.setEnabled(a, on, ::toast) }, onDelete = ::deleteWithUndo,
            )

            if (sections.ended.isNotEmpty()) {
                item(key = "ended_header") {
                    EndedHeader(sections.ended.size, endedExpanded, { endedExpanded = !endedExpanded }, vm::clearEnded)
                }
                if (endedExpanded) {
                    items(sections.ended, key = { it.id }) { alarm ->
                        EndedRow(alarm, onRestore = { vm.restore(alarm, ::toast) }, modifier = Modifier.animateItem())
                    }
                    item(key = "ended_footer") {
                        Text(
                            "Cleared automatically ${TimeUnit.MILLISECONDS.toDays(AlarmRepository.ENDED_RETENTION_MS)} days after they end.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    editing?.let { alarm ->
        AlarmEditorSheet(
            initial = alarm,
            recentLabels = sections.recentLabels,
            onDismiss = { editing = null },
            onSave = { vm.save(it, ::toast); editing = null },
            onDelete = { deleteWithUndo(it); editing = null },
            onSkip = { a, skip -> if (skip) vm.skipNext(a) else vm.undoSkip(a) },
        )
    }
}

/** New alarms default to the next full hour. */
private fun newAlarm(): Alarm {
    val next = LocalTime.now().plusHours(1)
    return Alarm(hour = next.hour, minute = 0, temporary = true)
}

private fun LazyListScope.alarmSection(
    title: String,
    alarms: List<Alarm>,
    now: Long,
    onClick: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onDelete: (Alarm) -> Unit,
) {
    if (alarms.isEmpty()) return
    item(key = "header_$title") { SectionHeader(title) }
    items(alarms, key = { it.id }) { alarm ->
        SwipeToDelete(onDelete = { onDelete(alarm) }, modifier = Modifier.animateItem()) {
            AlarmRow(alarm, now, onClick = { onClick(alarm) }, onToggle = { onToggle(alarm, it) })
        }
    }
}

@Composable
private fun NextAlarmCard(next: Pair<Alarm, Long>?, now: Long) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("next_alarm"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (next == null) {
                Text("No upcoming alarms", style = MaterialTheme.typography.titleLarge)
                return@Column
            }
            val (alarm, at) = next
            Text("Next alarm", style = MaterialTheme.typography.labelLarge)
            Text(
                Format.until(at, now).replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                listOfNotNull(
                    "${Format.day(at)} ${Format.time(context, at)}",
                    alarm.label.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun QuickAddField(vm: AlarmsViewModel, now: Long, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    // Re-parse when the minute changes too, so "7:15" keeps resolving to the right am/pm.
    val result = remember(text, now / 60_000) { vm.parse(text) }
    val success = (result as? QuickAddResult.Success)?.alarm

    fun submit() {
        val q = success ?: return
        vm.quickAdd(q, onSaved)
        text = ""
        focus.clearFocus()
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("quick_add"),
        placeholder = { Text("Quick add: “7:15 gym”, “nap 20m”") },
        singleLine = true,
        trailingIcon = {
            if (success != null) {
                IconButton(onClick = ::submit, modifier = Modifier.testTag("quick_add_submit")) {
                    Icon(Icons.Default.Check, contentDescription = "Add alarm")
                }
            }
        },
        supportingText = when {
            text.isBlank() -> null
            success != null -> ({ Text(describe(context, success), modifier = Modifier.testTag("quick_add_preview")) })
            text.trim().length >= 3 -> ({ Text((result as QuickAddResult.Failure).reason) })
            else -> null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        shape = RoundedCornerShape(16.dp),
    )
}

/** "Tomorrow 7:15 AM · Gym · ends after ringing" — shows how the text was understood before adding it. */
private fun describe(context: Context, q: QuickAlarm): String {
    val time = Format.time(context, q.hour, q.minute)
    val whenText = if (q.days != 0) {
        Format.repeatSummary(q.days)
    } else {
        val probe = Alarm(hour = q.hour, minute = q.minute, dateEpochDay = q.date?.toEpochDay())
        NextTrigger.compute(probe, ZonedDateTime.now())?.let { Format.day(it.toLocalDate()) } ?: ""
    }
    return listOfNotNull(
        "$whenText $time".trim(),
        q.label.takeIf { it.isNotBlank() },
        if (q.temporary) "ends after ringing" else null,
    ).joinToString(" · ")
}

@Composable
private fun AlarmRow(alarm: Alarm, now: Long, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val (time, suffix) = Format.timeParts(context, alarm.hour, alarm.minute)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("alarm_${alarm.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.55f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, style = MaterialTheme.typography.displaySmall)
                    if (suffix.isNotEmpty()) {
                        Text(suffix, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
                    }
                }
                if (alarm.label.isNotBlank()) {
                    Text(alarm.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    subtitle(context, alarm, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("toggle_${alarm.id}"))
        }
    }
}

private fun subtitle(context: Context, alarm: Alarm, now: Long): String {
    val parts = mutableListOf<String>()
    val next = alarm.nextTriggerAt
    parts += when {
        alarm.isRepeating -> Format.repeatSummary(alarm.days)
        next != null -> Format.day(next)
        alarm.dateEpochDay != null -> Format.day(LocalDate.ofEpochDay(alarm.dateEpochDay))
        else -> "Once"
    }
    val snoozed = alarm.snoozedUntil
    val skip = alarm.skipUntil
    when {
        snoozed != null && snoozed > now -> parts += "snoozed until ${Format.time(context, snoozed)}"
        alarm.enabled && skip != null && skip > now -> parts += "skipping ${Format.day(skip)}"
    }
    if (alarm.enabled && next != null && snoozed == null) parts += Format.until(next, now)
    return parts.joinToString(" · ")
}

@Composable
private fun EndedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Recently ended ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClear) { Text("Clear all") }
    }
}

@Composable
private fun EndedRow(alarm: Alarm, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).testTag("ended_${alarm.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(Format.time(context, alarm.hour, alarm.minute), alarm.label.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val verb = if (alarm.endReason == EndReason.MISSED) "Missed" else "Rang"
            Text(
                "$verb ${Format.day(alarm.endedAt ?: 0).replaceFirstChar { it.lowercaseChar() }}",
                style = MaterialTheme.typography.bodySmall,
                color = if (alarm.endReason == EndReason.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRestore, modifier = Modifier.testTag("restore_${alarm.id}")) { Text("Restore") }
    }
}

/** Surfaces anything that would stop alarms from ringing properly, with a one-tap fix. */
@Composable
private fun PermissionWarnings() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val issues = remember(refresh) { permissionIssues(context) }
    if (issues.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        issues.forEach { (message, intent) ->
            Card(
                onClick = { runCatching { context.startActivity(intent) } },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("Fix", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun permissionIssues(context: Context): List<Pair<String, Intent>> {
    val app = context.app
    val pkg = context.packageName
    return buildList {
        if (!app.notifications.areEnabled()) {
            add("Notifications are off, so alarms can't show." to
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        }
        if (Build.VERSION.SDK_INT >= 34 && !app.notifications.canUseFullScreen()) {
            add("Full-screen alarms are blocked, so alarms won't wake the screen." to
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
        }
        if (Build.VERSION.SDK_INT >= 31 && !app.canScheduleExact()) {
            add("Exact alarms aren't allowed, so alarms may ring late." to
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
        }
    }
}
