package com.alarmclockpro

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.alarmclockpro.ui.alarms.AlarmsScreen
import com.alarmclockpro.ui.settings.SettingsScreen
import com.alarmclockpro.ui.stopwatch.StopwatchScreen
import com.alarmclockpro.ui.theme.ClockTheme
import com.alarmclockpro.ui.timer.TimerScreen
import com.alarmclockpro.ui.worldclock.WorldClockScreen

class MainActivity : ComponentActivity() {
    private var requestedTab by mutableIntStateOf(TAB_ALARMS)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedTab = tabFor(intent)
        setContent {
            ClockTheme {
                // Exposes Modifier.testTag as resource-id so UI automation (Maestro, uiautomator) can find elements.
                Box(Modifier.semantics { testTagsAsResourceId = true }) { MainScreen(requestedTab) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab = tabFor(intent)
    }

    private fun tabFor(intent: Intent?) = when (intent?.action) {
        android.provider.AlarmClock.ACTION_SHOW_ALARMS -> TAB_ALARMS
        android.provider.AlarmClock.ACTION_SHOW_TIMERS -> TAB_TIMER
        else -> intent?.getIntExtra(EXTRA_TAB, TAB_ALARMS) ?: TAB_ALARMS
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_ALARMS = 0
        const val TAB_CLOCK = 1
        const val TAB_TIMER = 2
        const val TAB_STOPWATCH = 3
    }
}

private data class Tab(val title: String, val icon: Int, val selectedIcon: Int)

private val tabs = listOf(
    Tab("Alarm", R.drawable.ic_alarm_vector, R.drawable.ic_alarm_filled_vector),
    Tab("Clock", R.drawable.ic_clock_vector, R.drawable.ic_clock_filled_vector),
    Tab("Timer", R.drawable.ic_hourglass_vector, R.drawable.ic_hourglass_filled_vector),
    Tab("Stopwatch", R.drawable.ic_stopwatch_vector, R.drawable.ic_stopwatch_filled_vector),
)

@Composable
private fun MainScreen(requestedTab: Int) {
    var tab by rememberSaveable { mutableIntStateOf(requestedTab) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(requestedTab) { tab = requestedTab }

    // Alarms can't show without notifications on Android 13+, so ask up front.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(painterResource(if (tab == i) t.selectedIcon else t.icon), contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        // Each tab draws its own top bar (status-bar inset), so only apply the bottom bar's padding.
        val modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        val openSettings = { showSettings = true }
        when (tab) {
            MainActivity.TAB_ALARMS -> AlarmsScreen(snackbar, openSettings, modifier)
            MainActivity.TAB_CLOCK -> WorldClockScreen(openSettings, modifier)
            MainActivity.TAB_TIMER -> TimerScreen(openSettings, modifier)
            else -> StopwatchScreen(openSettings, modifier)
        }
    }
}
