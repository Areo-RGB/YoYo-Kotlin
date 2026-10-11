package com.example.yoyo.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoyo.AppController
import com.example.yoyo.AppTab
import com.example.yoyo.domain.Athlete
import com.example.yoyo.domain.AthleteStatus
import com.example.yoyo.domain.ShuttlePhase
import com.example.yoyo.domain.TestState
import com.example.yoyo.domain.fixed1
import com.example.yoyo.domain.formatTime
import com.example.yoyo.domain.protocolFor

private const val MIN_TILE_HEIGHT = 64f
private const val MAX_TILE_HEIGHT = 160f

private data class StatusCounts(val running: Int, val warned: Int, val out: Int)

private fun countStatuses(athletes: List<Athlete>): StatusCounts = StatusCounts(
    running = athletes.count { it.status == AthleteStatus.Running },
    warned = athletes.count { it.status == AthleteStatus.Warned },
    out = athletes.count { it.status == AthleteStatus.Eliminated },
)

/** Live tab outside an active test: idle start prompt or the finished summary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveScreen(controller: AppController) {
    val protocol = protocolFor(controller.selectedTestType)
    val list = controller.selectedAthletes
    val counts = countStatuses(list)
    Screen(
        eyebrow = "Live",
        title = protocol.type.label,
        description = "Tap Miss to warn an athlete — a second miss finishes them. Out finishes immediately.",
    ) {
        Column(Modifier.fillMaxWidth()) {
            DistanceMeter(controller)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountTile(counts.running, "running", Modifier.weight(1f))
                CountTile(counts.warned, "warned", Modifier.weight(1f))
                CountTile(counts.out, "finished", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            if (controller.testState == TestState.Idle) {
                Notice(
                    title = "No active test",
                    message = "Select athletes in the roster, then start the test. The clock follows the protocol audio.",
                    color = Blue,
                    icon = Icons.Outlined.Info,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedAppButton("Open roster", { controller.selectTab(AppTab.Setup) })
                    PrimaryButton("Start test", if (list.isEmpty()) null else controller::startTest)
                }
                Spacer(Modifier.height(16.dp))
            }
            LiveAthleteGrid(controller, list, fillHeight = null)
            Spacer(Modifier.height(16.dp))
            when (controller.testState) {
                TestState.Running, TestState.Paused -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (controller.undoDescription != null) {
                        TextAppButton("Undo", controller::undoLastAction, Icons.AutoMirrored.Filled.Undo)
                    }
                    if (controller.testState == TestState.Running) {
                        OutlinedAppButton("Pause", controller::pauseTest)
                    } else {
                        PrimaryButton("Resume", controller::resumeTest)
                    }
                    DangerButton("Finish test", controller::stopAndFinishTest)
                }
                TestState.Completed -> OutlinedAppButton("View results", { controller.selectTab(AppTab.Results) })
                TestState.Idle -> Unit
            }
        }
    }
}

/** Focus-mode live test: HUD on top, athlete grid filling the middle, controls pinned below. */
@Composable
fun LiveActiveScreen(controller: AppController, wide: Boolean) {
    val gutter = if (wide) 24.dp else 10.dp
    Column(Modifier.fillMaxSize().background(Color.White)) {
        LiveTelemetryHud(controller)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fill = maxHeight - 20.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = gutter, top = 8.dp, end = gutter, bottom = 12.dp),
            ) {
                LiveAthleteGrid(controller, controller.selectedAthletes, fillHeight = fill)
            }
        }
        LiveFixedBottomControls(controller)
    }
}

/**
 * Active athletes (running or warned) stay in roster order so a tile never
 * moves under the coach's finger between the warning and the second tap.
 * Finished athletes drop below them, best result first.
 */
@Composable
private fun LiveAthleteGrid(controller: AppController, athletes: List<Athlete>, fillHeight: Dp?) {
    val spacing = 8.dp
    val columns = 2
    val active = athletes.filter { it.status != AthleteStatus.Eliminated }
    val finished = athletes.filter { it.status == AthleteStatus.Eliminated }.sortedBy { it.rank ?: 999 }
    val rows = (athletes.size + columns - 1) / columns
    val tileHeight = if (fillHeight == null || rows == 0) {
        MIN_TILE_HEIGHT.dp
    } else {
        ((fillHeight - spacing * (rows - 1)) / rows).coerceIn(MIN_TILE_HEIGHT.dp, MAX_TILE_HEIGHT.dp)
    }
    GridRows(active + finished, columns, spacing, key = Athlete::id) { athlete ->
        Box(Modifier.height(tileHeight)) {
            LiveAthleteTile(controller, athlete, tileHeight)
        }
    }
}

@Composable
private fun LiveTelemetryHud(controller: AppController) {
    val protocol = protocolFor(controller.selectedTestType)
    val paused = controller.testState == TestState.Paused
    val counts = countStatuses(controller.selectedAthletes)
    val runtime = controller.runtime
    val countdown = runtime.elapsedMs < 0
    val running = runtime.phase == ShuttlePhase.Running
    val phaseColor by animateColorAsState(
        when {
            paused -> Charcoal
            countdown -> Orange
            running -> Green
            else -> Purple
        },
        animationSpec = tween(250),
        label = "phase",
    )
    val phaseLabel = when {
        paused -> "PAUSED"
        countdown -> "GET READY"
        running -> "RUN"
        else -> "RECOVERY"
    }
    val next = protocol.shuttles.getOrNull(runtime.shuttleIndex + 1)
    val detail = when {
        countdown -> "First shuttle ${runtime.shuttle.levelDisplay} · ${runtime.shuttle.speedKmh.fixed1()} km/h"
        !running && next != null -> "Next ${next.levelDisplay} · ${next.speedKmh.fixed1()} km/h"
        else -> "${runtime.shuttle.speedKmh.fixed1()} km/h · shuttle ${runtime.shuttle.shuttleNumber}/${protocol.shuttles.size}"
    }
    val onPhase = TextStyle(color = Color.White)

    Column(
        Modifier
            .fillMaxWidth()
            .background(phaseColor)
            .windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    phaseLabel,
                    style = onPhase,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    if (countdown) "Start" else "${runtime.currentDistanceMeters} m",
                    style = onPhase,
                    maxLines = 1,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
            }
            Text(
                runtime.phaseRemainingSeconds.fixed1(),
                style = onPhase.copy(fontFeatureSettings = TABULAR_FIGURES),
                fontSize = 52.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp,
            )
            Text(
                "s",
                style = onPhase,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (countdown) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
            )
        } else {
            ProgressBar(
                progress = runtime.phaseProgress.toFloat(),
                height = 6.dp,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                detail,
                style = onPhase,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Level ${runtime.shuttle.levelDisplay} · ${formatTime(runtime.elapsedMs)}",
                style = onPhase.copy(fontFeatureSettings = TABULAR_FIGURES),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusDotCount(counts.running, "running", Color.White)
            StatusDotCount(counts.warned, "warned", Color(0xffffb3b8))
            StatusDotCount(counts.out, "out", Color.White.copy(alpha = 0.54f))
        }
    }
}

@Composable
private fun StatusDotCount(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text("$count $label", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ProgressBar(progress: Float, height: Dp, color: Color, trackColor: Color) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

/**
 * One athlete tile. The first tap warns (red border); the second tap
 * finishes the athlete at the current distance and greys the tile out.
 */
@Composable
private fun LiveAthleteTile(controller: AppController, athlete: Athlete, height: Dp) {
    val canRecord = controller.canRecord
    val view = LocalView.current
    val scale = (height.value / MIN_TILE_HEIGHT).coerceIn(1f, 1.6f)
    val status = athlete.status
    val out = status == AthleteStatus.Eliminated
    val warned = status == AthleteStatus.Warned
    val accent = when (status) {
        AthleteStatus.Running -> Green
        AthleteStatus.Warned -> Red
        AthleteStatus.Eliminated -> Color(0xffa8a8a8)
    }
    val statusLine = when (status) {
        AthleteStatus.Running -> if (canRecord) "Tap to warn" else "Running"
        AthleteStatus.Warned -> if (canRecord) "Tap again = out" else "Warned at ${athlete.warningLevel}"
        AthleteStatus.Eliminated -> "Out · ${athlete.finalDistanceMeters ?: 0} m · ${athlete.finalLevel ?: ""}"
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                when {
                    warned -> Color(0xfffff1f1)
                    out -> Layer
                    else -> Color.White
                },
            )
            .border(if (warned) 1.5.dp else 1.dp, if (warned) Red else BorderColor, shape)
            .clickable(enabled = canRecord && !out) {
                if (warned) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    controller.eliminate(athlete.id)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    controller.markMiss(athlete.id)
                }
            }
            .drawBehind {
                drawRect(accent, size = size.copy(width = 5.dp.toPx()))
            }
            .padding(start = 13.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AthleteAvatar(athlete.name, (36 * scale).dp, Modifier.alpha(if (out) 0.5f else 1f))
        Spacer(Modifier.width((8 * scale).dp))
        Column(Modifier.weight(1f)) {
            Text(
                athlete.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = (16 * scale).sp,
                fontWeight = FontWeight.Bold,
                color = if (out) Muted else TextColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                statusLine,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = (12 * scale).sp,
                fontWeight = if (warned) FontWeight.Bold else FontWeight.Medium,
                color = if (warned) Red else Muted,
            )
        }
        val rank = athlete.rank
        if (out && rank != null) {
            Text(
                "#$rank",
                color = Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun LiveFixedBottomControls(controller: AppController) {
    var confirmFinish by remember { mutableStateOf(false) }
    val isRunning = controller.testState == TestState.Running
    val canUndo = controller.undoDescription != null
    val shape = RoundedCornerShape(8.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                drawLine(BorderColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = controller::undoLastAction,
            enabled = canUndo,
            shape = shape,
            border = BorderStroke(1.dp, if (canUndo) Blue else BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(width = 56.dp, height = 52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        if (isRunning) {
            OutlinedButton(
                onClick = controller::pauseTest,
                shape = shape,
                border = BorderStroke(1.dp, Muted),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pause", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = controller::resumeTest,
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Resume", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = { confirmFinish = true },
            shape = shape,
            border = BorderStroke(1.dp, Red.copy(alpha = 140f / 255f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.height(52.dp),
        ) {
            Text("Finish test", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (confirmFinish) {
        val remaining = controller.selectedAthletes.count { it.status != AthleteStatus.Eliminated }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = Color.White,
            title = { Text("Finish test?") },
            text = {
                Text(
                    if (remaining == 0) {
                        "All athletes are out. Results will be finalised."
                    } else {
                        "$remaining athlete${if (remaining == 1) "" else "s"} still in the test will be scored at the current distance."
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text("Keep running") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmFinish = false
                        controller.stopAndFinishTest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                ) { Text("Finish") }
            },
        )
    }
}

@Composable
private fun DistanceMeter(controller: AppController) {
    val protocol = protocolFor(controller.selectedTestType)
    val runtime = controller.runtime
    val stateColor = when (controller.testState) {
        TestState.Running -> Green
        TestState.Paused -> Purple
        TestState.Completed -> Blue
        TestState.Idle -> Muted
    }
    Panel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(20.dp),
        color = Layer,
        borderColor = Color.Transparent,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(controller.testState.name.uppercase(), stateColor)
                Spacer(Modifier.width(9.dp))
                Text(
                    protocol.type.label,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(runtime.elapsedMs),
                    color = Muted,
                    fontSize = 18.sp,
                    style = TextStyle(fontFeatureSettings = TABULAR_FIGURES),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                runtime.currentDistanceMeters.toString(),
                fontSize = 58.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = TextColor,
            )
            Spacer(Modifier.height(7.dp))
            Text("METERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Muted)
            Spacer(Modifier.height(18.dp))
            Text("Protocol progress", fontSize = 16.sp, color = TextColor)
            Spacer(Modifier.height(16.dp))
            ProgressBar(runtime.progress.toFloat(), 8.dp, Blue, LayerTwo)
            Spacer(Modifier.height(7.dp))
            Text(
                "${runtime.shuttle.levelDisplay} · shuttle ${runtime.shuttle.shuttleNumber} of ${protocol.shuttles.size}",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth > 650.dp) 4 else 2
                val tiles = listOf(
                    "Level" to runtime.shuttle.levelDisplay,
                    "Speed" to "${runtime.shuttle.speedKmh.fixed1()} km/h",
                    "Shuttle" to "${runtime.shuttle.shuttleNumber}/${protocol.shuttles.size}",
                    when {
                        runtime.elapsedMs < 0 -> "Starts in"
                        runtime.phase == ShuttlePhase.Running -> "Run time"
                        else -> "Rest time"
                    } to "${runtime.phaseRemainingSeconds.fixed1()} s",
                )
                GridRows(tiles, columns, 8.dp) { (label, value) ->
                    StatTile(label, value, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
