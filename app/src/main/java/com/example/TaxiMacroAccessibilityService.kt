package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

enum class MacroState {
    IDLE,
    LAUNCHING_TARGET,
    CLICKING_MY_LOCATION,
    SCANNING_POINTS,
    PARSING_DATA,
    ROUTING_YANDEX
}

data class ScanPoint(
    val name: String,
    val xPercent: Float,
    val yPercent: Float,
    val mockLat: Double,
    val mockLon: Double
)

data class NodeWithBounds(
    val text: String,
    val rect: Rect
)

data class ParsedCoeff(
    val coeff: Float,
    val rect: Rect
)

class TaxiMacroAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var macroJob: Job? = null

    companion object {
        var instance: TaxiMacroAccessibilityService? = null
            private set

        val macroState = MutableStateFlow(MacroState.IDLE)
        val logs = MutableStateFlow<List<String>>(emptyList())

        fun log(message: String) {
            val currentList = logs.value.toMutableList()
            // Add timestamp/prefix or format it
            val timePref = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()
            currentList.add(0, "[$timePref] $message")
            // Cap at 100 entries to prevent memory bounds overflow
            if (currentList.size > 100) {
                currentList.removeAt(currentList.size - 1)
            }
            logs.value = currentList
        }

        fun clearLogs() {
            logs.value = emptyList()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        log("Связь со службой автоматизации успешно установлена!")
        Toast.makeText(this, "Служба автоматизации подключена!", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used to track window transition details or logs
    }

    override fun onInterrupt() {
        log("Служба доступности была прервана.")
        macroJob?.cancel()
        macroState.value = MacroState.IDLE
    }

    override fun onDestroy() {
        instance = null
        log("Служба доступности отключена.")
        macroJob?.cancel()
        super.onDestroy()
    }

    /**
     * Helper to perform click gesture asynchronously using Coroutines
     */
    private suspend fun clickAt(x: Float, y: Float): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 80)
        gestureBuilder.addStroke(strokeDescription)

        try {
            dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)
        } catch (e: Exception) {
            log("Gesture Exception: ${e.message}")
            if (continuation.isActive) continuation.resume(false)
        }
    }

    /**
     * Traverses the node tree finding text strings and bounding locations
     */
    private fun traverseAndExtract(
        node: AccessibilityNodeInfo?,
        texts: MutableList<String>,
        boundsList: MutableList<NodeWithBounds>
    ) {
        if (node == null) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            texts.add(text)
            val rect = Rect()
            node.getBoundsInScreen(rect)
            boundsList.add(NodeWithBounds(text, rect))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseAndExtract(child, texts, boundsList)
            child?.recycle()
        }
    }

    /**
     * Main task executing macro workflow
     */
    fun startMacroTask(context: Context) {
        if (macroJob?.isActive == true) {
            log("Запуск автоматизации уже активен. Перезапуск...")
            macroJob?.cancel()
        }

        macroJob = serviceScope.launch {
            try {
                clearLogs()
                macroState.value = MacroState.LAUNCHING_TARGET
                log("Инициализация сценария Такси-Макроса...")

                val targetPackage = AppPrefs.getTargetPackage(context)
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    log("Запуск целевого приложения [$targetPackage]...")
                    context.startActivity(launchIntent)
                } else {
                    log("Приложение [$targetPackage] не найдено на устройстве. Запуск тестового симулятора в приложении...")
                    val selfIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("START_SIMULATION", true)
                    }
                    context.startActivity(selfIntent)
                }

                log("Ожидание 2.5 сек для инициализации координат...")
                delay(2500)

                // Retrieve screen width/height dynamically
                val metrics = resources.displayMetrics
                val w = metrics.widthPixels.toFloat()
                val h = metrics.heightPixels.toFloat()
                log("Разрешение экрана определено: ${w.toInt()}x${h.toInt()} пикселей")

                // Step 1: Center My Location (x: 50%, y: 85%)
                macroState.value = MacroState.CLICKING_MY_LOCATION
                val locX = w * 0.5f
                val locY = h * 0.85f
                log("Шаг 1: Клик по кнопке геопозиции в (${locX.toInt()}, ${locY.toInt()})")
                val clickOk = clickAt(locX, locY)
                if (clickOk) {
                    log("Жест клика геопозиции успешно отправлен.")
                } else {
                    log("Ошибка отправки клика геопозиции.")
                }

                log("Ожидание 2 сек для анимации карты...")
                delay(2000)

                // Step 2: Sequentially scan 4 offset points around map center
                macroState.value = MacroState.SCANNING_POINTS
                val scanPoints = listOf(
                    ScanPoint("Северо-Запад", w * 0.35f, h * 0.35f, 55.7558, 37.6173),
                    ScanPoint("Северо-Восток", w * 0.65f, h * 0.35f, 55.7600, 37.6300),
                    ScanPoint("Юго-Запад", w * 0.35f, h * 0.65f, 55.7480, 37.6050),
                    ScanPoint("Юго-Восток", w * 0.65f, h * 0.65f, 55.7450, 37.6250)
                )

                for ((index, pt) in scanPoints.withIndex()) {
                    log("Шаг 2.${index + 1}: Клик по смещенной точке [${pt.name}] в (${pt.xPercent.toInt()}, ${pt.yPercent.toInt()})")
                    val gestureOk = clickAt(pt.xPercent, pt.yPercent)
                    if (gestureOk) {
                        log("Сканирование зоны [${pt.name}] запущено.")
                    } else {
                        log("Ошибка отправки жеста в зону [${pt.name}].")
                    }
                    log("Ожидание 2 сек для обновления коэффициентов спроса...")
                    delay(2000)
                }

                // Step 3: Screen Parsing
                macroState.value = MacroState.PARSING_DATA
                log("Шаг 3: Чтение разметки экрана узлов дерева (задержка сбора 1.5 сек)...")
                delay(1500)

                val rootNode = rootInActiveWindow
                val textValues = mutableListOf<String>()
                val boundsList = mutableListOf<NodeWithBounds>()

                if (rootNode != null) {
                    traverseAndExtract(rootNode, textValues, boundsList)
                    rootNode.recycle()
                } else {
                    log("Предупреждение: Не удалось получить активное окно. Убедитесь, что экран включен.")
                }

                log("Найдено ${textValues.size} текстовых элементов на экране.")

                // Scan for floats (1.0 - 6.0) representing coefficients (e.g., "1.5", "2.1", "x2.4")
                val regex = """[xX]?\s*([1-9]\d*(?:\.\d+)?|\d+\.\d+)""".toRegex()
                val parsedCoeffs = mutableListOf<ParsedCoeff>()

                for (item in boundsList) {
                    regex.findAll(item.text).forEach { match ->
                        val plainNum = match.groupValues[1]
                        val floatVal = plainNum.toFloatOrNull()
                        if (floatVal != null && floatVal in 1.0f..6.0f) {
                            parsedCoeffs.add(ParsedCoeff(floatVal, item.rect))
                        }
                    }
                }

                log("Распознано возможных коэффициентов спроса: ${parsedCoeffs.size}")
                for (c in parsedCoeffs) {
                    log("Найден коэффициент: ${c.coeff}x в координатах центра (${c.rect.centerX()}, ${c.rect.centerY()})")
                }

                // Map coefficients to screen quadrants
                val zoneCoeffs = mutableMapOf<Int, Float>()
                for (i in 0..3) {
                    zoneCoeffs[i] = 1.0f // base factor
                }

                val cx = w / 2f
                val cy = h / 2f

                for (cand in parsedCoeffs) {
                    val nodeCenterX = cand.rect.centerX()
                    val nodeCenterY = cand.rect.centerY()

                    val targetZoneIdx = when {
                        nodeCenterX < cx && nodeCenterY < cy -> 0  // Top-Left
                        nodeCenterX >= cx && nodeCenterY < cy -> 1 // Top-Right
                        nodeCenterX < cx && nodeCenterY >= cy -> 2 // Bottom-Left
                        else -> 3                                // Bottom-Right
                    }
                    if (cand.coeff > (zoneCoeffs[targetZoneIdx] ?: 1.0f)) {
                        zoneCoeffs[targetZoneIdx] = cand.coeff
                    }
                }

                log("Сводные результаты: СЗ=${zoneCoeffs[0]}x | СВ=${zoneCoeffs[1]}x | ЮЗ=${zoneCoeffs[2]}x | ЮВ=${zoneCoeffs[3]}x")

                // Identify highest multiplier
                var bestIdx = 0
                var highestVal = 1.0f
                for (i in 0..3) {
                    val v = zoneCoeffs[i] ?: 1.0f
                    if (v > highestVal) {
                        highestVal = v
                        bestIdx = i
                    }
                }

                val bestPoint = scanPoints[bestIdx]
                log("Выгодная зона определена! Группа [${bestPoint.name}] дает наивысший коэффициент: ${highestVal}x")

                // Step 4: Routing
                macroState.value = MacroState.ROUTING_YANDEX
                val navType = AppPrefs.getNavigatorType(context)
                val targetScheme = if (navType == "navigator") "yandexnavi" else "yandexmaps"
                val targetPkg = if (navType == "navigator") "ru.yandex.yandexnavi" else "ru.yandex.yandexmaps"
                val appLabel = if (navType == "navigator") "Яндекс Навигатор" else "Яндекс Карты"

                log("Шаг 4: Построение оптимального маршрута до [${bestPoint.name}] (${bestPoint.mockLat}, ${bestPoint.mockLon}) в $appLabel...")

                val yandexUri = "$targetScheme://build_route_on_map?lat_to=${bestPoint.mockLat}&lon_to=${bestPoint.mockLon}"
                val routingIntent = Intent(Intent.ACTION_VIEW, Uri.parse(yandexUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(targetPkg)
                }

                if (routingIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(routingIntent)
                    log("Маршрут со спецификатором пакета отправлен напрямую в $appLabel!")
                } else {
                    // Try the fallback Yandex application before opening web browser
                    val fallbackPkg = if (navType == "navigator") "ru.yandex.yandexmaps" else "ru.yandex.yandexnavi"
                    val fallbackScheme = if (navType == "navigator") "yandexmaps" else "yandexnavi"
                    val fallbackLabel = if (navType == "navigator") "Яндекс Карты" else "Яндекс Навигатор"

                    val fallbackAppUri = "$fallbackScheme://build_route_on_map?lat_to=${bestPoint.mockLat}&lon_to=${bestPoint.mockLon}"
                    val fallbackAppIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackAppUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage(fallbackPkg)
                    }

                    if (fallbackAppIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(fallbackAppIntent)
                        log("Выбранный навигатор не запущен, маршрут открыт через альтернативное приложение $fallbackLabel.")
                    } else {
                        log("Приложения Яндекса не обнаружены. Открытие веб-карты...")
                        val webUrl = "https://yandex.ru/maps/?rtext=~${bestPoint.mockLat},${bestPoint.mockLon}&rtt=auto"
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                    }
                }

                delay(1200)
                log("Сценарий Такси-Макроса успешно выполнен! 🏁")
                macroState.value = MacroState.IDLE

            } catch (e: Exception) {
                log("Сценарий прерван с ошибкой: ${e.localizedMessage}")
                macroState.value = MacroState.IDLE
            }
        }
    }
}
