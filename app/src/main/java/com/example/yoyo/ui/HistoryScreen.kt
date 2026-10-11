package com.example.yoyo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoyo.AppController
import com.example.yoyo.domain.TestSession
import com.example.yoyo.domain.fixed1
import com.example.yoyo.domain.formatTime

@Composable
fun HistoryScreen(controller: AppController) {
    Screen(
        eyebrow = "Archive",
        title = "Test history",
        description = "Saved sessions and result details are stored locally on this device.",
    ) {
        if (controller.sessions.isEmpty()) {
            EmptyState(
                title = "No saved sessions yet",
                copy = "Complete a test and save it to build your local history.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                controller.sessions.forEach { session ->
                    androidx.compose.runtime.key(session.id) {
                        HistoryCard(controller, session)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(controller: AppController, session: TestSession) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(session.id) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(session.title, fontWeight = FontWeight.Bold, color = TextColor)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${session.testType.label} · ${AppController.shortDate(session.timestampMs)} · " +
                            "${session.completedAthletesCount}/${session.totalAthletesCount} athletes",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Muted,
                    modifier = Modifier.rotate(chevron),
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete session", tint = TextColor)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Best distance", "${session.maxDistanceAchieved} m", Modifier.weight(1f))
                        StatTile("Best level", session.maxLevelAchieved, Modifier.weight(1f))
                        StatTile("Duration", formatTime(session.durationSeconds * 1000), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    session.results.forEach { result ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${result.rank}.", color = TextColor, modifier = Modifier.width(30.dp))
                            Text(
                                result.athleteName,
                                fontWeight = FontWeight.SemiBold,
                                color = TextColor,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${result.finalDistanceMeters} m · ${result.finalLevel} · ${result.vo2Max.fixed1()}",
                                color = TextColor,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            title = { Text("Delete session?") },
            text = { Text("Remove “${session.title}” from this device?") },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        controller.deleteSession(session.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
                ) { Text("Delete") }
            },
        )
    }
}
