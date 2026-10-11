package com.example.yoyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoyo.AppController
import com.example.yoyo.domain.Athlete
import com.example.yoyo.domain.TestState
import com.example.yoyo.domain.TestType
import com.example.yoyo.domain.fullNameFor
import com.example.yoyo.domain.startLevelOptions

@Composable
fun RosterScreen(controller: AppController) {
    Screen(
        eyebrow = "Setup",
        title = "Athlete roster",
        description = "Choose the athletes participating in this test.",
        action = {
            Pill("${controller.selectedAthletes.size}/${controller.athletes.size} selected", color = Blue)
        },
    ) {
        RosterSelector(controller)
    }
}

@Composable
private fun RosterSelector(controller: AppController) {
    val locked = controller.testState != TestState.Idle
    var name by rememberSaveable { mutableStateOf("") }
    val submit = {
        controller.addAthlete(name)
        name = ""
    }
    Column(Modifier.fillMaxWidth()) {
        Panel(
            modifier = Modifier.fillMaxWidth(),
            padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            color = Layer,
            borderColor = Color.Transparent,
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ToolButton("Select all", if (locked) null else controller::selectAll)
                Spacer(Modifier.width(8.dp))
                ToolButton("Clear", if (locked) null else controller::deselectAll)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (controller.selectedTestType == TestType.YoYoIr1) {
            Panel(
                modifier = Modifier.fillMaxWidth(),
                padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                color = Layer,
                borderColor = Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Starting speed level", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextColor)
                    Spacer(Modifier.width(12.dp))
                    StartLevelDropdown(controller, locked, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (controller.athletes.isEmpty()) {
            EmptyState(
                title = "No athletes in roster",
                copy = "Add an athlete below or restore the default squad.",
                action = {
                    SmallButton("Restore default squad", if (locked) null else controller::resetRoster, Icons.Filled.Refresh)
                },
            )
            Spacer(Modifier.height(12.dp))
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth > 600.dp) 2 else 1
                GridRows(controller.athletes, columns, 6.dp, key = Athlete::id) { athlete ->
                    RosterRow(controller, athlete, locked)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        enabled = !locked,
                        singleLine = true,
                        label = { Text("Add athlete") },
                        placeholder = { Text("Athlete name (max 40 characters)", maxLines = 1) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Layer,
                            unfocusedContainerColor = Layer,
                            disabledContainerColor = Layer,
                            focusedBorderColor = Blue,
                            unfocusedBorderColor = BorderColor,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedAppButton("Add athlete", if (locked) null else submit, Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Protocol: ") }
                        append(controller.selectedTestType.badges.joinToString(" · "))
                    },
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun StartLevelDropdown(controller: AppController, locked: Boolean, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options = startLevelOptions()
    val selected = options.firstOrNull { it.speedLevel == controller.selectedYoYoStartLevel } ?: options.first()
    val shape = RoundedCornerShape(6.dp)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, BorderColor, shape)
                .clickable(enabled = !locked) { expanded = true }
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (locked) Muted else TextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Muted)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        controller.setStartLevel(option.speedLevel)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RosterRow(controller: AppController, athlete: Athlete, locked: Boolean) {
    val fullName = fullNameFor(athlete.name)
    val selected = athlete.isSelected
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color.White else Layer)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Blue else BorderColor, shape)
            .clickable(enabled = !locked) { controller.toggleAthlete(athlete.id) }
            .padding(horizontal = 12.dp, vertical = if (locked) 10.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = if (selected) "Selected" else "Not selected",
            tint = if (selected) Blue else Muted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        AthleteAvatar(athlete.name, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                athlete.name,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = TextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fullName != athlete.name) {
                Text(fullName, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!locked) {
            IconButton(onClick = { controller.removeAthlete(athlete.id) }) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${athlete.name}",
                    tint = Red.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
