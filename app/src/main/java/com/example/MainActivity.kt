package com.example

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
                    containerColor = Color(0xFF0C101B) // Cosmic Slate background
                ) { innerPadding ->
                    DashboardScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State trackers
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context, TaxiMacroAccessibilityService::class.java))
    }
    var isOverlayRunning by remember { mutableStateOf(FloatingService.isRunning) }

    val macroState by TaxiMacroAccessibilityService.macroState.collectAsState()
    val rawLogs by TaxiMacroAccessibilityService.logs.collectAsState()

    // Periodic status updates
    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context, TaxiMacroAccessibilityService::class.java)
            isOverlayRunning = FloatingService.isRunning
            delay(1000)
        }
    }

    // Interactive simulated coefficients
    var coeffTopLeft by remember { mutableStateOf(3.2f) }
    var coeffTopRight by remember { mutableStateOf(1.8f) }
    var coeffBottomLeft by remember { mutableStateOf(2.5f) }
    var coeffBottomRight by remember { mutableStateOf(4.1f) }

    // Tap indicators for simulated radar zone flashes
    var flashTopLeft by remember { mutableStateOf(false) }
    var flashTopRight by remember { mutableStateOf(false) }
    var flashBottomLeft by remember { mutableStateOf(false) }
    var flashBottomRight by remember { mutableStateOf(false) }

    var tabSelected by remember { mutableStateOf(0) } // 0: Config/Setup, 1: Simulated Map Playground

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        // App Premium Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF161B2B), Color(0xFF0B0E14))))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Automation Logo",
                    tint = Color(0xFF00FF88),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "РАДАР-АВТОМАТ",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Макросы такси и служба доступности",
                        color = Color(0xFF8B9CB4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Navigation Tabs (Setup vs Simulated Map)
        TabRow(
            selectedTabIndex = tabSelected,
            containerColor = Color(0xFF111728),
            contentColor = Color(0xFF00FF88)
        ) {
            Tab(
                selected = tabSelected == 0,
                onClick = { tabSelected = 0 },
                text = { Text("НАСТРОЙКА", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = tabSelected == 1,
                onClick = { tabSelected = 1 },
                text = { Text("ЭМУЛЯТОР КАРТЫ", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        if (tabSelected == 0) {
            // Setup & Status Console
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning Banner if Permissions are Missing
                if (!hasOverlayPermission || !isAccessibilityEnabled) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1921)),
                            border = BorderStroke(1.dp, Color(0xFFEF5350)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Предупреждение",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Требуются разрешения",
                                        color = Color(0xFFEF5350),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Для работы плавающего оверлея и автоматической отправки кликов предоставьте разрешения на отображение поверх других окон и службу доступности ниже.",
                                        color = Color(0xFFE2C4C6),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Configuration Permission Cards
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "СИСТЕМНЫЕ РАЗРЕШЕНИЯ",
                            color = Color(0xFF8B9CB4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // 1. SYSTEM_ALERT_WINDOW CARD
                        PermissionStatusCard(
                            title = "Отображение поверх других окон",
                            description = "Позволяет разместить круглую плавающую кнопку «СТАРТ» поверх сторонних картографических программ.",
                            isGranted = hasOverlayPermission,
                            onGrantClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            btnTag = "overlay_permission_btn"
                        )

                        // 2. ACCESSIBILITY BIND CARD
                        PermissionStatusCard(
                            title = "Служба автоматизации доступности",
                            description = "Необходимо для автоматической отправки кликов и считывания коэффициентов цен непосредственно с экрана.",
                            isGranted = isAccessibilityEnabled,
                            onGrantClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            btnTag = "accessibility_permission_btn"
                        )
                    }
                }

                // Automation settings for package name and Yandex application target
                item {
                    var targetPackage by remember { mutableStateOf(AppPrefs.getTargetPackage(context)) }
                    var navigatorType by remember { mutableStateOf(AppPrefs.getNavigatorType(context)) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121829)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "НАСТРОЙКИ КОНФИГУРАЦИИ",
                                color = Color(0xFF8B9CB4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            // Target Package Input field
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Пакет приложения Радара спроса:",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedTextField(
                                    value = targetPackage,
                                    onValueChange = { newValue ->
                                        targetPackage = newValue
                                        AppPrefs.setTargetPackage(context, newValue)
                                    },
                                    placeholder = { Text("Пример: com.example.radar", color = Color(0xFF475569)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("radar_package_input"),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00FF88),
                                        unfocusedBorderColor = Color(0xFF1E293B),
                                        focusedContainerColor = Color(0xFF0C101B),
                                        unfocusedContainerColor = Color(0xFF0C101B)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Text(
                                    text = "Идентификатор (ID) пакета, который запускается при старте макроса (например, com.radar.coefficients или любой другой).",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }

                            Divider(color = Color(0xFF1E293B))

                            // Yandex application type chooser
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Тип целевого навигатора:",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Яндекс Навигатор Button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (navigatorType == "navigator") Color(0xFF1A332B) else Color(0xFF0C101B))
                                            .border(
                                                width = 1.dp,
                                                color = if (navigatorType == "navigator") Color(0xFF00FF88) else Color(0xFF1E293B),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                navigatorType = "navigator"
                                                AppPrefs.setNavigatorType(context, "navigator")
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Яндекс Навигатор",
                                                color = if (navigatorType == "navigator") Color(0xFF00FF88) else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "yandexnavi://",
                                                color = Color(0xFF64748B),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    // Яндекс Карты Button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (navigatorType == "maps") Color(0xFF1A332B) else Color(0xFF0C101B))
                                            .border(
                                                width = 1.dp,
                                                color = if (navigatorType == "maps") Color(0xFF00FF88) else Color(0xFF1E293B),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                navigatorType = "maps"
                                                AppPrefs.setNavigatorType(context, "maps")
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Яндекс Карты",
                                                color = if (navigatorType == "maps") Color(0xFF00FF88) else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "yandexmaps://",
                                                color = Color(0xFF64748B),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Foreground Service Dashboard controls
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121829)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Панель управления оверлеем",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isOverlayRunning) "Служба запущена в фоне" else "Служба неактивна",
                                        color = if (isOverlayRunning) Color(0xFF00FF88) else Color(0xFF8B9CB4),
                                        fontSize = 12.sp
                                    )
                                }

                                Switch(
                                    checked = isOverlayRunning,
                                    onCheckedChange = {
                                        if (hasOverlayPermission) {
                                            val serviceIntent = Intent(context, FloatingService::class.java)
                                            if (isOverlayRunning) {
                                                context.stopService(serviceIntent)
                                            } else {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    context.startForegroundService(serviceIntent)
                                                } else {
                                                    context.startService(serviceIntent)
                                                }
                                            }
                                        } else {
                                            val uri = Uri.parse("package:${context.packageName}")
                                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                                            context.startActivity(intent)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00FF88),
                                        checkedTrackColor = Color(0xFF1E3A2F)
                                    ),
                                    modifier = Modifier.testTag("floating_service_switch")
                                )
                            }
                        }
                    }
                }

                // Running Terminal Event Logs
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF060912)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (macroState != MacroState.IDLE) Color(0xFF00FF88) else Color.DarkGray)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ЖУРНАЛ СОБЫТИЙ МАКРОСА",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                TextButton(
                                    onClick = { TaxiMacroAccessibilityService.clearLogs() },
                                    modifier = Modifier.testTag("clear_logs_btn")
                                ) {
                                    Text("ОЧИСТИТЬ", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Divider(color = Color(0xFF1E293B), modifier = Modifier.padding(bottom = 8.dp))

                            if (rawLogs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Системный терминал бездействует. Нажмите кнопку «СТАРТ» на оверлее, чтобы начать запись...",
                                        color = Color(0xFF475569),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    reverseLayout = false
                                ) {
                                    items(rawLogs) { logMsg ->
                                        Text(
                                            text = logMsg,
                                            color = if (logMsg.contains("успешно") || logMsg.contains("определена")) Color(0xFF00FF88)
                                            else if (logMsg.contains("ошибка") || logMsg.contains("прерван")) Color(0xFFEF5350)
                                            else if (logMsg.contains("Шаг")) Color(0xFF38BDF8)
                                            else Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Simulated Map Playground view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "СИМУЛЯТОР КАРТЫ СПРОСА (РАДАР)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Виртуальное поле, где координаты соответствуют целям автоматизации. Нажмите на зоны для изменения коэффициентов спроса.",
                    color = Color(0xFF8B9CB4),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Virtual Phone Map Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF10131B))
                        .border(2.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                ) {
                    // Coordinates Grid
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // Top-Left Sector
                            MapQuadrantSector(
                                title = "СЕВЕРО-ЗАПАДНАЯ ЗОНА\n(55.7558, 37.6173)",
                                multiplier = coeffTopLeft,
                                isFlashed = flashTopLeft,
                                onClick = {
                                    coeffTopLeft = if (coeffTopLeft >= 5.0f) 1.2f else coeffTopLeft + 0.6f
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("sector_tl")
                            )

                            // Top-Right Sector
                            MapQuadrantSector(
                                title = "СЕВЕРО-ВОСТОЧНАЯ ЗОНА\n(55.7600, 37.6300)",
                                multiplier = coeffTopRight,
                                isFlashed = flashTopRight,
                                onClick = {
                                    coeffTopRight = if (coeffTopRight >= 5.0f) 1.2f else coeffTopRight + 0.6f
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("sector_tr")
                            )
                        }

                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // Bottom-Left Sector
                            MapQuadrantSector(
                                title = "ЮГО-ЗАПАДНАЯ ЗОНА\n(55.7480, 37.6050)",
                                multiplier = coeffBottomLeft,
                                isFlashed = flashBottomLeft,
                                onClick = {
                                    coeffBottomLeft = if (coeffBottomLeft >= 5.0f) 1.2f else coeffBottomLeft + 0.6f
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("sector_bl")
                            )

                            // Bottom-Right Sector
                            MapQuadrantSector(
                                title = "ЮГО-ВОСТОЧНАЯ ЗОНА\n(55.7450, 37.6250)",
                                multiplier = coeffBottomRight,
                                isFlashed = flashBottomRight,
                                onClick = {
                                    coeffBottomRight = if (coeffBottomRight >= 5.0f) 1.2f else coeffBottomRight + 0.6f
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("sector_br")
                            )
                        }
                    }

                    // Centeral Anchor Target Frame
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xE01E293B))
                            .border(1.dp, Color(0xFF38BDF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Me",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "БАЗА GPS",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Simulate the clicks visually by listening to layout scans
                    LaunchedEffect(macroState) {
                        when (macroState) {
                            MacroState.CLICKING_MY_LOCATION -> {
                                // Flashes the center base
                            }
                            MacroState.SCANNING_POINTS -> {
                                // Cycle flashes in sequence to simulate actual physical scans!
                                flashTopLeft = true
                                delay(300)
                                flashTopLeft = false
                                delay(1700)

                                flashTopRight = true
                                delay(300)
                                flashTopRight = false
                                delay(1700)

                                flashBottomLeft = true
                                delay(300)
                                flashBottomLeft = false
                                delay(1700)

                                flashBottomRight = true
                                delay(300)
                                flashBottomRight = false
                            }
                            else -> {
                                flashTopLeft = false
                                flashTopRight = false
                                flashBottomLeft = false
                                flashBottomRight = false
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Instructions
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tips",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Подсказка: Включите панель плавающих кнопок во вкладке НАСТРОЙКА. Нажмите «СТАРТ» на оверлее и следите за тем, как макрос сканирует этот экран, считывает коэффициенты, находит лучшую зону и строит маршрут в навигаторе!",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MapQuadrantSector(
    title: String,
    multiplier: Float,
    isFlashed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBg by animateColorAsState(
        targetValue = if (isFlashed) Color(0xFF2E3D48) else Color(0xFF111420),
        animationSpec = tween(150),
        label = "sector_bg"
    )

    val borderStrokeColor = if (isFlashed) Color(0xFF00FF88) else Color(0xFF1E293B)

    // Layout representation matching coordinates scanned by gesture builder
    Box(
        modifier = modifier
            .background(containerBg)
            .border(0.5.dp, borderStrokeColor)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color(0xFF64748B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 11.sp
            )

            // Dynamic demand graphic circle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Glow circles representing the surge coefficients
                val glowColor = when {
                    multiplier >= 3.5f -> Color(0xFFFF3366) // Extreme demand (Hot pink)
                    multiplier >= 2.5f -> Color(0xFFFF9900) // Medium surge (Gold)
                    else -> Color(0xFF00C853) // Minimal surge (Emerald)
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(glowColor.copy(alpha = 0.12f))
                        .border(1.dp, glowColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "x%.1f".format(multiplier),
                        color = glowColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Text(
                text = "НАЖМИТЕ ДЛЯ ИЗМЕНЕНИЯ",
                color = Color(0xFF475569),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
    btnTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121829)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = "Статус",
                        tint = if (isGranted) Color(0xFF00FF88) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    color = Color(0xFF8B9CB4),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag(btnTag)
                ) {
                    Text("ВКЛЮЧИТЬ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "АКТИВНО",
                    color = Color(0xFF00FF88),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

/**
 * Checks if the target accessibility service is active
 */
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
    val compName = ComponentName(context, serviceClass)
    val enabledSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledSetting)
    while (splitter.hasNext()) {
        val path = splitter.next()
        val cName = ComponentName.unflattenFromString(path)
        if (cName != null && cName == compName) {
            return true
        }
    }
    return false
}
