package io.github.nrupeshsurya.wisp.ui.ring

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nrupeshsurya.wisp.alarm.RingService
import io.github.nrupeshsurya.wisp.alarm.RingTarget
import io.github.nrupeshsurya.wisp.alarm.Ringing
import io.github.nrupeshsurya.wisp.app
import io.github.nrupeshsurya.wisp.ui.Format
import io.github.nrupeshsurya.wisp.ui.components.rememberNow
import io.github.nrupeshsurya.wisp.ui.theme.ClockTheme

/** Full-screen ring UI. Shows over the lock screen; finishes itself when nothing is ringing any more. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The ring screen is always dark, so force light status/navigation bar icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            ClockTheme(dark = true) {
                val target by Ringing.current.collectAsState()
                LaunchedEffect(target == null) { if (target == null) finish() }
                // Back must not silently dismiss an alarm.
                BackHandler {}
                Box(Modifier.semantics { testTagsAsResourceId = true }) { target?.let { RingScreen(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Ringing.current.value != null) RingService.control(this, RingService.ACTION_UI_VISIBLE)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun intent(context: Context) = Intent(context, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun RingScreen(target: RingTarget) {
    val context = LocalContext.current
    val now = rememberNow(1_000L)
    val snoozeMinutes = context.app.prefs.snoozeMinutes

    val (title, detail, primary, secondary) = when (target) {
        is RingTarget.AlarmRing -> {
            val a = target.alarm
            RingTexts(
                title = a.label.ifBlank { "Alarm" },
                detail = if (a.temporary) "Temporary — will be moved to Recently ended" else Format.repeatSummary(a.days).takeIf { a.isRepeating } ?: "",
                primary = "Dismiss" to RingService.ACTION_DISMISS,
                secondary = "Snooze $snoozeMinutes min" to RingService.ACTION_SNOOZE,
            )
        }
        is RingTarget.TimerRing -> RingTexts(
            title = target.timer.label.ifBlank { "Time's up" },
            detail = "Timer · ${Format.duration(target.timer.durationMs)}",
            primary = "Stop" to RingService.ACTION_DISMISS,
            secondary = "+1 min" to RingService.ACTION_ADD_MINUTE,
        )
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            Format.time(context, now),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        // The label is the point of the alarm, so it is the biggest thing after the time.
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("ring_title"),
        )
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1.4f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilledTonalButton(
                onClick = { RingService.control(context, secondary.second) },
                modifier = Modifier.weight(1f).height(72.dp).testTag("ring_secondary"),
            ) { Text(secondary.first, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { RingService.control(context, primary.second) },
                modifier = Modifier.weight(1f).height(72.dp).testTag("ring_primary"),
            ) { Text(primary.first, style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class RingTexts(
    val title: String,
    val detail: String,
    val primary: Pair<String, String>,
    val secondary: Pair<String, String>,
)
