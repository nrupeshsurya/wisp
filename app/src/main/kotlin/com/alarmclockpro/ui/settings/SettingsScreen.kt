package com.alarmclockpro.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmclockpro.BuildConfig
import com.alarmclockpro.app
import com.alarmclockpro.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    var snooze by remember { mutableIntStateOf(prefs.snoozeMinutes) }
    var silence by remember { mutableIntStateOf(prefs.autoSilenceMinutes) }
    var gradual by remember { mutableStateOf(prefs.gradualVolume) }
    var choosing by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Alarms")
            ListItem(
                headlineContent = { Text("Snooze length") },
                supportingContent = { Text("$snooze minutes") },
                modifier = Modifier.clickable { choosing = "snooze" },
            )
            ListItem(
                headlineContent = { Text("Silence after") },
                supportingContent = { Text("$silence minutes of ringing") },
                modifier = Modifier.clickable { choosing = "silence" },
            )
            ListItem(
                headlineContent = { Text("Gradually increase volume") },
                supportingContent = { Text("Starts quiet and reaches full volume over 30 seconds") },
                trailingContent = { Switch(checked = gradual, onCheckedChange = { gradual = it; prefs.gradualVolume = it }) },
            )
            ListItem(
                headlineContent = { Text("Alarm volume") },
                supportingContent = { Text("Uses the system alarm volume") },
            )
            SectionHeader("About")
            ListItem(
                headlineContent = { Text("Alarm Clock Pro ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("Based on Fossify Clock. Licensed under GPL-3.0.") },
            )
        }
    }

    when (choosing) {
        "snooze" -> ChoiceDialog("Snooze length", listOf(5, 10, 15, 20, 30), snooze, { snooze = it; prefs.snoozeMinutes = it }) { choosing = null }
        "silence" -> ChoiceDialog("Silence after", listOf(1, 5, 10, 15, 30), silence, { silence = it; prefs.autoSilenceMinutes = it }) { choosing = null }
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column {
                options.forEach { v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(v); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = v == selected, onClick = { onSelect(v); onDismiss() })
                        Text("$v minutes", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}
