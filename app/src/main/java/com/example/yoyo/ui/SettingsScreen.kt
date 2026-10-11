package com.example.yoyo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoyo.AppController
import com.example.yoyo.domain.TestType

@Composable
fun SettingsScreen(controller: AppController) {
    Screen(
        eyebrow = "Configuration",
        title = "Settings",
        description = "Display, audio and preferences.",
    ) {
        Column(Modifier.fillMaxWidth()) {
            SwitchPanel(
                title = "Fullscreen mode",
                caption = "Expand the tracker to fill the entire display during testing.",
                checked = controller.fullscreen,
                onChange = controller::changeFullscreen,
            )
            Spacer(Modifier.height(12.dp))
            SwitchPanel(
                title = "Sound cues",
                caption = "Muting changes gain only — the protocol clock keeps running.",
                checked = controller.soundEnabled,
                onChange = controller::changeSoundEnabled,
            )
            Spacer(Modifier.height(12.dp))
            TestType.entries.forEach { type ->
                AudioCacheCard(controller, type)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SwitchPanel(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, color = TextColor, modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = onChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = Color.White),
                )
            }
            Text(caption, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AudioCacheCard(controller: AppController, type: TestType) {
    val cached = type in controller.cachedAudio
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("${type.label} Protocol Audio", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bundled in the app and available offline for repeat starts.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                Pill(if (cached) "Cached" else "Offline Ready", if (cached) Green else Muted)
            }
            Spacer(Modifier.height(13.dp))
            TextAppButton(
                if (cached) "Re-cache bundled audio" else "Cache bundled audio",
                { controller.cacheAudio(type) },
                Icons.Outlined.Download,
            )
        }
    }
}
