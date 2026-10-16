package com.rakkez.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0F2A2B)
private val Panel = Color(0xFF163A3C)
private val Line = Color(0xFF2A5659)
private val TextC = Color(0xFFEAF2EF)
private val Mute = Color(0xFF9DB8B4)
private val Acc = Color(0xFFFFB703)
private val Ok = Color(0xFF5FD6A0)
private val Bad = Color(0xFFFF6B5A)

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Acc, onPrimary = Color(0xFF2B1D00),
                    background = Bg, onBackground = TextC,
                    surface = Panel, onSurface = TextC,
                    surfaceVariant = Panel, onSurfaceVariant = Mute,
                    outline = Line, error = Bad
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = Bg) { Screen() }
                }
            }
        }
    }

    fun hasUsage(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) ==
                AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay() = Settings.canDrawOverlays(this)

    fun hasBattery() = (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(packageName)

    fun syncService() {
        if ((Store.isLocked(this) || Store.remoteUrl(this).isNotBlank()) && hasUsage()) {
            try { startForegroundService(Intent(this, BlockerService::class.java)) } catch (_: Exception) {}
        }
    }
}

@Composable
fun Panel2(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun Screen() {
    val act = LocalContext.current as MainActivity
    var tasks by remember { mutableStateOf(Store.load(act)) }
    var activeId by remember { mutableLongStateOf(Store.activeId(act)) }
    var activeStart by remember { mutableLongStateOf(Store.activeStart(act)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var usageOk by remember { mutableStateOf(act.hasUsage()) }
    var overlayOk by remember { mutableStateOf(act.hasOverlay()) }
    var batteryOk by remember { mutableStateOf(act.hasBattery()) }
    var free by remember { mutableStateOf(Store.isFree(act)) }
    var showAdmin by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("60") }

    fun reload() {
        tasks = Store.load(act)
        activeId = Store.activeId(act)
        activeStart = Store.activeStart(act)
        free = Store.isFree(act)
        usageOk = act.hasUsage(); overlayOk = act.hasOverlay(); batteryOk = act.hasBattery()
        act.syncService()
    }

    fun live(t: StudyTask): Int =
        t.studiedSec + if (activeId == t.id) ((now - activeStart) / 1000).toInt().coerceAtLeast(0) else 0

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reload() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // عدّاد الشاشة + إيقاف تلقائي لما الوقت يكمل
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
            val id = activeId
            if (id != 0L) {
                val t = tasks.firstOrNull { it.id == id }
                if (t != null && live(t) >= t.minutes * 60) { Store.stopActive(act); reload() }
            }
        }
    }

    val left = tasks.count { !it.done }
    val permsOk = usageOk && overlayOk

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ركّز 📚", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdmin = true }) { Text("🔑 الأخ الكبير", color = Mute) }
            }
        }
        item {
            val (msg, col) = when {
                free && left > 0 -> "🔓 القفل ملغي من الأخ الكبير" to Ok
                tasks.isNotEmpty() && left == 0 -> "🎉 خلّصت كل مهامك! الموبايل مفتوح" to Ok
                left > 0 && !permsOk -> "⚠️ القفل مش شغال، فعّل الصلاحيات تحت" to Bad
                left > 0 -> "🔒 القفل شغال، فاضل $left مهمة" to Acc
                else -> "ضيف مهام اليوم عشان تبدأ" to Mute
            }
            Panel2 { Text(msg, color = col, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        if (!usageOk) item {
            Button(onClick = { act.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("1) فعّل Usage Access") }
        }
        if (!overlayOk) item {
            Button(onClick = {
                act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${act.packageName}")))
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("2) فعّل Display over other apps") }
        }
        if (!batteryOk) item {
            OutlinedButton(onClick = {
                act.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${act.packageName}")))
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("3) اسمح بالتشغيل في الخلفية (مهم)") }
        }
        item {
            Panel2 {
                Text("مهمة جديدة", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("المادة") },
                        modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(mins, { mins = it.filter(Char::isDigit) }, label = { Text("دقايق (10+)") },
                        modifier = Modifier.width(96.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Button(onClick = {
                    val m = mins.toIntOrNull() ?: 0
                    if (name.isNotBlank() && m >= 10) {
                        val nm = name.trim()
                        Store.save(act, Store.load(act) + StudyTask(System.currentTimeMillis(), nm, m))
                        Store.log(act, "ADD", "➕ أضاف مهمة \"$nm\"")
                        name = ""; reload()
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("إضافة") }
            }
        }
        if (tasks.isEmpty()) item {
            Text("مفيش مهام لسه. اكتب المادة والدقايق وادوس إضافة.", color = Mute)
        }
        items(tasks, key = { it.id }) { t ->
            val sec = live(t)
            val isActive = activeId == t.id
            val canFinish = sec >= t.minutes * 30
            Panel2(Modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (t.done) "✅ " else "") + t.name, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${t.minutes} دقيقة", color = Mute)
                }
                Text("%02d:%02d".format(sec / 60, sec % 60), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (isActive) Acc else TextC)
                LinearProgressIndicator(
                    progress = { (sec / (t.minutes * 60f)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (t.done) Ok else Acc, trackColor = Bg
                )
                if (!t.done) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) OutlinedButton(onClick = { Store.stopActive(act); reload() },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("⏸ إيقاف") }
                        else Button(onClick = { Store.start(act, t.id); reload() },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("▶ ابدأ") }
                        Button(enabled = canFinish, onClick = {
                            if (activeId == t.id) Store.stopActive(act)
                            Store.save(act, Store.load(act).map { if (it.id == t.id) it.copy(done = true) else it })
                            Store.log(act, "DONE", "✅ خلّص \"${t.name}\"")
                            reload()
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Text(if (canFinish) "خلصت ✅" else "بعد نص الوقت")
                        }
                    }
                }
            }
        }
    }

    if (showAdmin) AdminDialog(
        act = act, free = free, tasks = tasks, activeId = activeId, activeStart = activeStart,
        onFree = { v -> Store.setFree(act, v); reload() },
        onReset = {
            Store.stopActive(act)
            Store.save(act, Store.load(act).map { it.copy(studiedSec = 0, done = false, sessions = 0) })
            Store.log(act, "RESET", "🔄 صفّر وقت المهام")
            reload()
        },
        onDeleteTask = { id ->
            val t = Store.load(act).firstOrNull { it.id == id }
            if (activeId == id) Store.stopActive(act)
            Store.save(act, Store.load(act).filter { it.id != id })
            if (t != null) Store.log(act, "DELETE", "🗑 حذف مهمة \"${t.name}\"")
            reload()
        },
        onDismiss = { showAdmin = false }
    )
}

private fun fmtDur(sec: Int): String {
    val h = sec / 3600
    val mi = (sec % 3600) / 60
    return if (h > 0) "$h ساعة و $mi دقيقة" else "$mi دقيقة"
}

@Composable
fun AdminDialog(
    act: MainActivity, free: Boolean, tasks: List<StudyTask>, activeId: Long, activeStart: Long,
    onFree: (Boolean) -> Unit, onReset: () -> Unit, onDeleteTask: (Long) -> Unit, onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var log by remember { mutableStateOf(Store.loadLog(act)) }
    val fmtDate = remember { SimpleDateFormat("d/M  HH:mm", Locale.US) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize().systemBarsPadding().padding(10.dp),
            shape = RoundedCornerShape(22.dp), color = Bg, contentColor = TextC
        ) {
            if (!ok) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🔑 الأخ الكبير", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit); err = false },
                        label = { Text("الرقم السري") }, singleLine = true, isError = err,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    Button(onClick = {
                        if (pin == Store.pin(act)) { ok = true; log = Store.loadLog(act) } else { err = true; pin = "" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("دخول") }
                    if (err) Text("الرقم السري غلط", color = Bad)
                    TextButton(onClick = onDismiss) { Text("إغلاق", color = Mute) }
                }
            } else {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val dayStart = cal.timeInMillis
                val today = log.filter { it.ts >= dayStart }
                var studied = today.filter { it.type == "STOP" }.sumOf { it.sec }
                if (activeId != 0L && activeStart > 0L) {
                    val liveEl = ((System.currentTimeMillis() - maxOf(activeStart, dayStart)) / 1000).toInt().coerceAtLeast(0)
                    studied += liveEl
                }
                val doneToday = today.count { it.type == "DONE" }
                val blocksToday = today.count { it.type == "BLOCK" }

                LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔑 الأخ الكبير", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text("إغلاق", color = Mute) }
                        }
                    }
                    item {
                        Panel2 {
                            Text("📊 ملخص النهارده", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("⏱ ذاكر: ${fmtDur(studied)}")
                            Text("✅ مهام خلصت: $doneToday")
                            Text("🚫 محاولات فتح تطبيق ممنوع: $blocksToday", color = if (blocksToday > 0) Bad else TextC)
                        }
                    }
                    item { Text("📋 المهام", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    if (tasks.isEmpty()) item { Text("مفيش مهام", color = Mute) }
                    items(tasks, key = { "t" + it.id }) { t ->
                        val liveSec = t.studiedSec +
                            if (activeId == t.id) ((System.currentTimeMillis() - activeStart) / 1000).toInt().coerceAtLeast(0) else 0
                        Panel2 {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text((if (t.done) "✅ " else "") + t.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text("ذاكر ${minOf(liveSec, t.minutes * 60) / 60} من ${t.minutes} دقيقة  •  ${t.sessions} جلسة", color = Mute)
                                }
                                TextButton(onClick = { onDeleteTask(t.id); log = Store.loadLog(act) }) {
                                    Text("حذف", color = Bad)
                                }
                            }
                        }
                    }
                    item {
                        Panel2 {
                            Text(if (free) "القفل ملغي حاليًا" else "القفل شغال")
                            Button(onClick = { onFree(!free); log = Store.loadLog(act) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (free) "🔒 رجّع القفل" else "🔓 إلغاء القفل")
                            }
                            OutlinedButton(onClick = { onReset(); log = Store.loadLog(act) }, modifier = Modifier.fillMaxWidth()) {
                                Text("تصفير وقت المهام")
                            }
                            OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit) },
                                label = { Text("رقم سري جديد (4 أرقام+)") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedButton(onClick = {
                                if (newPin.length >= 4) { Store.setPin(act, newPin); newPin = "" }
                            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الرقم") }
                        }
                    }
                    item {
                        var url by remember { mutableStateOf(Store.remoteUrl(act)) }
                        var saved by remember { mutableStateOf(false) }
                        Panel2 {
                            Text("🌐 المتابعة عن بُعد", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("كود العائلة (اكتبه في صفحة المتابعة):", color = Mute)
                            Text(Store.familyCode(act), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Acc)
                            OutlinedTextField(url, { url = it; saved = false }, label = { Text("رابط قاعدة بيانات Firebase") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                            Button(onClick = { Store.setRemoteUrl(act, url); Remote.sync(act); act.syncService(); saved = true },
                                Modifier.fillMaxWidth()) { Text("حفظ وإرسال الآن") }
                            if (saved) Text("اتبعت ✅ افتح صفحة المتابعة وشوف", color = Ok)
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🕘 سجل النشاط", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { Store.clearLog(act); log = emptyList() }) { Text("مسح السجل", color = Bad) }
                        }
                    }
                    if (log.isEmpty()) item { Text("السجل فاضي", color = Mute) }
                    items(log.asReversed()) { e ->
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Panel) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(fmtDate.format(Date(e.ts)), color = Mute, fontSize = 13.sp)
                                Text(e.text, modifier = Modifier.weight(1f),
                                    color = if (e.type == "BLOCK") Bad else TextC)
                            }
                        }
                    }
                }
            }
        }
    }
}
