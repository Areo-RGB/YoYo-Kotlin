package com.example.yoyo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoyo.AppController
import com.example.yoyo.AppTab
import com.example.yoyo.domain.TestState
import com.example.yoyo.domain.TestType
import kotlinx.coroutines.launch

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No snackbar host") }

val AppTab.icon: ImageVector
    get() = when (this) {
        AppTab.Setup -> Icons.Outlined.Group
        AppTab.Live -> Icons.Outlined.MonitorHeart
        AppTab.Results -> Icons.Outlined.EmojiEvents
        AppTab.History -> Icons.Outlined.Schedule
        AppTab.Settings -> Icons.Outlined.Settings
    }

private val AppController.isActiveTest: Boolean
    get() = testState == TestState.Running || testState == TestState.Paused

@Composable
fun TrackerShell(controller: AppController) {
    if (!controller.initialized) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue)
        }
        return
    }
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
            val wide = maxWidth >= 900.dp
            // The live HUD replaces the app bar so the athlete grid gets the room.
            val focusMode = controller.activeTab == AppTab.Live && controller.isActiveTest
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            val body: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        if (!focusMode) {
                            TopBar(
                                controller = controller,
                                showMenu = !wide,
                                showProtocolTabs = wide,
                                onMenu = { scope.launch { drawerState.open() } },
                            )
                        }
                        Row(
                            Modifier
                                .weight(1f)
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                        ) {
                            if (wide) DesktopNav(controller)
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                Content(controller, wide)
                            }
                        }
                    }
                    SnackbarHost(
                        snackbar,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                    )
                }
            }

            if (wide) {
                body()
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        AppDrawer(controller) { scope.launch { drawerState.close() } }
                    },
                    content = body,
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    controller: AppController,
    showMenu: Boolean,
    showProtocolTabs: Boolean,
    onMenu: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                drawLine(BorderColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showMenu) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation", tint = TextColor)
                }
            }
            Text(
                buildAnnotatedString {
                    append("Yo-Yo ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Fitness Tracker") }
                },
                color = TextColor,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (showMenu) 2.dp else 20.dp, end = 12.dp),
            )
            if (showProtocolTabs) {
                TestType.entries.forEach { type ->
                    val selected = controller.selectedTestType == type
                    Box(
                        Modifier
                            .height(48.dp)
                            .background(if (selected) TextColor else Color.Transparent)
                            .drawBehind {
                                if (selected) {
                                    val stroke = 3.dp.toPx()
                                    drawLine(
                                        Blue,
                                        Offset(0f, size.height - stroke / 2),
                                        Offset(size.width, size.height - stroke / 2),
                                        stroke,
                                    )
                                }
                            }
                            .clickable { controller.setTestType(type) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(type.label, color = if (selected) Color.White else Muted, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
        }
    }
}

@Composable
private fun DesktopNav(controller: AppController) {
    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color.White)
            .drawBehind {
                drawLine(BorderColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
        AppTab.entries.forEach { tab ->
            NavItem(tab, selected = controller.activeTab == tab) { controller.selectTab(tab) }
        }
    }
}

@Composable
private fun AppDrawer(controller: AppController, close: () -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = Color.White,
        drawerShape = RectangleShape,
        windowInsets = WindowInsets(0),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TextColor)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 18.dp),
        ) {
            Text("Yo-Yo Fitness Tracker", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Navigation", color = Color(0xffc6c6c6), fontSize = 12.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            Text(
                "PROTOCOL",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 4.dp),
            )
            TestType.entries.forEach { type ->
                val selected = controller.selectedTestType == type
                Text(
                    type.label,
                    color = if (selected) Blue else TextColor,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            controller.setTestType(type)
                            close()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = BorderColor)
            AppTab.entries.forEach { tab ->
                NavItem(tab, selected = controller.activeTab == tab) {
                    controller.selectTab(tab)
                    close()
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(if (selected) LayerTwo else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = null, tint = if (selected) TextColor else Muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            tab.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TextColor else Muted,
        )
    }
}

@Composable
private fun Content(controller: AppController, wide: Boolean) {
    val activeTest = controller.isActiveTest
    if (activeTest && controller.activeTab == AppTab.Live) {
        LiveActiveScreen(controller, wide)
        return
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        key(controller.activeTab) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(
                        PaddingValues(
                            start = if (wide) 20.dp else 16.dp,
                            top = 12.dp,
                            end = if (wide) 20.dp else 16.dp,
                            bottom = if (wide) 24.dp else 72.dp,
                        ),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 1200.dp).fillMaxWidth()) {
                    if (!activeTest) {
                        ProtocolSwitcher(controller)
                        Spacer(Modifier.height(12.dp))
                    }
                    when (controller.activeTab) {
                        AppTab.Setup -> RosterScreen(controller)
                        AppTab.Live -> LiveScreen(controller)
                        AppTab.Results -> ResultsScreen(controller)
                        AppTab.History -> HistoryScreen(controller)
                        AppTab.Settings -> SettingsScreen(controller)
                    }
                }
            }
        }
        if (!wide && controller.activeTab == AppTab.Setup) {
            FloatingStartBar(controller, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ProtocolSwitcher(controller: AppController) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, BorderColor, shape),
    ) {
        TestType.entries.forEach { type ->
            val selected = controller.selectedTestType == type
            val background by animateColorAsState(
                if (selected) TextColor else Color.White,
                animationSpec = tween(150),
                label = "protocol",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(background)
                    .clickable { controller.setTestType(type) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    type.label,
                    color = if (selected) Color.White else Muted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FloatingStartBar(controller: AppController, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                drawLine(BorderColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        val enabled = controller.selectedAthletes.isNotEmpty()
        androidx.compose.material3.Button(
            onClick = controller::startTest,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Blue,
                contentColor = Color.White,
                disabledContainerColor = LayerTwo,
                disabledContentColor = Muted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Start", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
