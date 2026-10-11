package com.example.yoyo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoyo.AppController
import com.example.yoyo.AppTab
import com.example.yoyo.domain.LeaderboardMetric
import com.example.yoyo.domain.TestState
import com.example.yoyo.domain.fixed1
import kotlinx.coroutines.launch

private val RankWidth = 64.dp
private val PlayerWidth = 200.dp
private val PrimaryWidth = 170.dp
private val TestsWidth = 72.dp
private val SecondaryWidth = 160.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsScreen(controller: AppController) {
    val leaderboard = controller.leaderboard
    val completed = controller.testState == TestState.Completed
    val rankByTotal = controller.leaderboardMetric == LeaderboardMetric.TotalDistance
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Screen(
        eyebrow = "Results",
        title = "Player leaderboard",
        description = if (rankByTotal) {
            "Ranked by total distance across saved test history. Players are matched by name."
        } else {
            "Ranked by average distance across saved test history. Players are matched by name."
        },
        action = {
            IconButton(onClick = controller::toggleLeaderboardMetric, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.SwapVert,
                    contentDescription = if (rankByTotal) "Rank by average distance" else "Rank by total distance",
                    tint = Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (completed) {
                val results = controller.results
                val saved = controller.sessionSavedId != null
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            if (saved) "Latest test saved to history" else "Finished test not yet saved",
                            fontWeight = FontWeight.Bold,
                            color = TextColor,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Only saved test results count towards leaderboard rankings.",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PrimaryButton(
                                if (saved) "Saved to history" else "Save to history",
                                if (saved || results.isEmpty()) null else controller::saveSession,
                            )
                            OutlinedAppButton(
                                "Copy test CSV",
                                if (results.isEmpty()) {
                                    null
                                } else {
                                    {
                                        clipboard.setText(AnnotatedString(controller.makeCsv()))
                                        scope.launch { snackbar.showSnackbar("Results CSV copied to clipboard.") }
                                    }
                                },
                                icon = Icons.Outlined.ContentCopy,
                            )
                            DangerButton("New test", controller::resetTest)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (leaderboard.isEmpty()) {
                Panel(Modifier.fillMaxWidth(), padding = PaddingValues(24.dp)) {
                    Text(
                        "No saved test results yet. Complete a test and save it to history to build the leaderboard.",
                        color = Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Row(Modifier.heightIn(min = 42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            HeaderCell("RANK", RankWidth)
                            HeaderCell("PLAYER", PlayerWidth)
                            HeaderCell(if (rankByTotal) "TOTAL (M) ↓" else "AVG DISTANCE (M) ↓", PrimaryWidth)
                            HeaderCell("TESTS", TestsWidth)
                            HeaderCell(if (rankByTotal) "AVG DISTANCE (M)" else "TOTAL (M)", SecondaryWidth)
                        }
                        leaderboard.forEach { entry ->
                            HorizontalDivider(
                                color = BorderColor,
                                modifier = Modifier.width(RankWidth + PlayerWidth + PrimaryWidth + TestsWidth + SecondaryWidth + 24.dp),
                            )
                            Row(
                                Modifier.heightIn(min = 52.dp).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${entry.rank}", color = TextColor, modifier = Modifier.width(RankWidth))
                                Row(Modifier.width(PlayerWidth), verticalAlignment = Alignment.CenterVertically) {
                                    AthleteAvatar(entry.playerName, 30.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(entry.playerName, fontWeight = FontWeight.SemiBold, color = TextColor, maxLines = 1)
                                }
                                Text(
                                    if (rankByTotal) "${entry.totalDistanceMeters}" else entry.averageDistanceMeters.fixed1(),
                                    fontWeight = FontWeight.Bold,
                                    color = TextColor,
                                    modifier = Modifier.width(PrimaryWidth),
                                )
                                Text("${entry.sessionCount}", color = TextColor, modifier = Modifier.width(TestsWidth))
                                Text(
                                    if (rankByTotal) entry.averageDistanceMeters.fixed1() else "${entry.totalDistanceMeters}",
                                    color = TextColor,
                                    modifier = Modifier.width(SecondaryWidth),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedAppButton("View history", { controller.selectTab(AppTab.History) }, Modifier.fillMaxWidth())
            if (!completed) {
                Spacer(Modifier.height(8.dp))
                OutlinedAppButton("Back to live test", { controller.selectTab(AppTab.Live) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeaderCell(label: String, width: Dp) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Muted,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}
