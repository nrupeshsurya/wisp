package com.alarmclockpro.ui.alarms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alarmclockpro.alarm.NextTrigger
import com.alarmclockpro.data.Alarm
import com.alarmclockpro.data.Days
import com.alarmclockpro.ui.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorSheet(
    initial: Alarm,
    recentLabels: List<String>,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onSkip: (Alarm, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isNew = initial.id == 0L
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeState = rememberTimePickerState(initial.hour, initial.minute, Format.is24h(context))
    var label by rememberSaveable { mutableStateOf(initial.label) }
    var days by rememberSaveable { mutableStateOf(initial.days) }
    var date by rememberSaveable { mutableStateOf(initial.dateEpochDay) }
    var temporary by rememberSaveable { mutableStateOf(initial.temporary || (isNew && initial.days == 0)) }
    var vibrate by rememberSaveable { mutableStateOf(initial.vibrate) }
    var soundUri by rememberSaveable { mutableStateOf(initial.soundUri) }
    var soundTitle by rememberSaveable { mutableStateOf(initial.soundTitle) }
    var showDatePicker by remember { mutableStateOf(false) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        val (uri, title) = describeSound(context, picked)
        soundUri = uri
        soundTitle = title
    }

    fun current() = initial.copy(
        hour = timeState.hour, minute = timeState.minute, label = label.trim(), days = days,
        dateEpochDay = date.takeIf { days == 0 }, temporary = temporary && days == 0,
        vibrate = vibrate, soundUri = soundUri, soundTitle = soundTitle,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            // The sheet is its own window, so test tags must be exposed here too.
            Modifier.semantics { testTagsAsResourceId = true }
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            TimePicker(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("label_field"),
            )
            val suggestions = recentLabels.filter { it != label.trim() }
            if (suggestions.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { s -> SuggestionChip(onClick = { label = s }, label = { Text(s) }) }
                }
            }

            Text("Repeat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
            DayToggles(days, onChange = { days = it })

            if (days == 0) {
                val effectiveDate = date?.let(LocalDate::ofEpochDay)
                    ?: NextTrigger.compute(Alarm(hour = timeState.hour, minute = timeState.minute), ZonedDateTime.now())!!.toLocalDate()
                ListItem(
                    headlineContent = { Text("Date") },
                    supportingContent = { Text(if (date == null) "${Format.day(effectiveDate)} (next occurrence)" else Format.day(effectiveDate)) },
                    trailingContent = {
                        Row {
                            if (date != null) TextButton(onClick = { date = null }) { Text("Reset") }
                            TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
                SwitchRow(
                    title = "Delete after it rings",
                    subtitle = "Moves to Recently ended, then is cleared after 7 days",
                    checked = temporary,
                    onChange = { temporary = it },
                    tag = "temporary_switch",
                )
            } else if (!isNew && initial.enabled && initial.isRepeating && initial.days == days) {
                val skipping = (initial.skipUntil ?: 0) > System.currentTimeMillis()
                val skipTarget = if (skipping) initial.skipUntil else initial.nextTriggerAt
                if (skipTarget != null) {
                    SwitchRow(
                        title = "Skip next",
                        subtitle = "${Format.day(skipTarget)} ${Format.time(context, skipTarget)}",
                        checked = skipping,
                        onChange = { onSkip(initial, it); onDismiss() },
                        tag = "skip_switch",
                    )
                }
            }

            ListItem(
                headlineContent = { Text("Sound") },
                supportingContent = { Text(soundTitle ?: if (soundUri == "") "Silent" else "Default alarm sound") },
                modifier = Modifier.padding(0.dp),
                trailingContent = { TextButton(onClick = { ringtonePicker.launch(ringtonePickerIntent(soundUri)) }) { Text("Change") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
            SwitchRow(title = "Vibrate", subtitle = null, checked = vibrate, onChange = { vibrate = it }, tag = "vibrate_switch")

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(current()) }, modifier = Modifier.testTag("save_alarm")) { Text("Save") }
            }
        }
    }

    if (showDatePicker) {
        val today = LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date?.let(LocalDate::ofEpochDay) ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // DatePicker works in UTC midnights.
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun DayToggles(days: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Format.orderedWeek().forEach { day ->
            val on = Days.has(days, day)
            val name = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            FilterChip(
                selected = on,
                onClick = { onChange(days xor Days.bit(day)) },
                label = { Text(name, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(44.dp).testTag("day_${day.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag)) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

private fun ringtonePickerIntent(current: String?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    .putExtra(
        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
        when (current) {
            null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            "" -> null
            else -> Uri.parse(current)
        },
    )

/** Maps the picker result to our storage: null = default sound, "" = silent. */
private fun describeSound(context: Context, picked: Uri?): Pair<String?, String?> = when {
    picked == null -> "" to "Silent"
    RingtoneManager.isDefault(picked) -> null to null
    else -> picked.toString() to runCatching { RingtoneManager.getRingtone(context, picked)?.getTitle(context) }.getOrNull()
}
