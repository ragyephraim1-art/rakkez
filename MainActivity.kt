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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    fun hasAccess(): Boolean {
        val cn = android.content.ComponentName(this, FocusAccessibilityService::class.java)
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return on.split(':').any {
            it.equals(cn.flattenToString(), ignoreCase = true) || it.equals(cn.flattenToShortString(), ignoreCase = true)
        }
    }

    fun hasBattery() = (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(packageName)

    fun syncService() {
        if (hasUsage()) {
            try { startForegroundService(Intent(this, BlockerService::class.java)) } catch (_: Exception) {}
        }
    }
}

fun fmt(s: Int) = "%02d:%02d".format(s / 60, s % 60)

fun loadApps(c: Context): List<Pair<String, String>> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != c.packageName }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}

fun logText(e: LogEntry): String = when (e.type) {
    "add" -> "➕ أضاف مهمة \"${e.name}\" (${e.extra} د)"
    "start" -> "▶ بدأ \"${e.name}\""
    "stop" -> "⏸ وقّف \"${e.name}\" بعد ${fmt(e.extra)}"
    "done" -> "✅ خلّص \"${e.name}\""
    "blocked" -> "🚫 حاول يفتح ${e.name}"
    "free" -> "🔓 الأخ الكبير ألغى القفل"
    "relock" -> "🔒 الأخ الكبير رجّع القفل"
    "reset" -> "↺ الأخ الكبير صفّر الوقت"
    "admdel" -> "🗑 الأخ الكبير حذف \"${e.name}\""
    else -> e.type
}

@Composable
fun Panel2(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun Stat(big: String, small: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Panel) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, color = Acc, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(small, color = Mute, fontSize = 11.sp)
        }
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
    var accessOk by remember { mutableStateOf(act.hasAccess()) }
    var free by remember { mutableStateOf(Store.isFree(act)) }
    var showAdd by remember { mutableStateOf(false) }
    var adminMode by remember { mutableIntStateOf(0) } // 0 مقفول، 1 رقم سري، 2 صفحة المتابعة

    fun refresh() {
        tasks = Store.load(act)
        activeId = Store.activeId(act)
        activeStart = Store.activeStart(act)
        free = Store.isFree(act)
        usageOk = act.hasUsage(); overlayOk = act.hasOverlay(); batteryOk = act.hasBattery(); accessOk = act.hasAccess()
    }

    fun reload() { refresh(); act.syncService() }

    fun live(t: StudyTask): Int =
        t.studiedSec + if (activeId == t.id) Store.elapsed(activeStart, now) else 0

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reload() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(1000)
            n++
            now = System.currentTimeMillis()
            if (n % 4 == 0) refresh()
            val id = activeId
            if (id != 0L) {
                val t = tasks.firstOrNull { it.id == id }
                if (t != null && live(t) >= t.minutes * 60) { Store.stopActive(act); reload() }
            }
        }
    }

    BackHandler(enabled = adminMode == 2) { adminMode = 0; reload() }

    val todays = tasks.filter { Store.isToday(it) }.sortedBy { it.startMin }
    val nowMin = Store.nowMin()
    val left = todays.count { !it.done && it.startMin <= nowMin }          // ميعادها جه وماخلصتش
    val later = todays.filter { !it.done && it.startMin > nowMin }          // لسه ماجاش ميعادها
    val permsOk = usageOk && overlayOk

    if (adminMode == 2) {
        AdminPage(act, onChange = { reload() }, onBack = { adminMode = 0; reload() })
    } else LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.rakkez_logo), contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Text("ركّز", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("v6", color = Mute, fontSize = 11.sp)
                TextButton(onClick = { adminMode = 1 }) { Text("🔑 الأخ الكبير", color = Mute) }
            }
        }
        item {
            val (msg, col) = when {
                free && left > 0 -> "🔓 القفل ملغي من الأخ الكبير" to Ok
                todays.isNotEmpty() && todays.all { it.done } -> "🎉 خلّصت كل مهامك! الموبايل مفتوح" to Ok
                left > 0 && !permsOk -> "⚠️ القفل مش شغال، فعّل الصلاحيات تحت" to Bad
                left > 0 -> "🔒 القفل شغال، فاضل $left مهمة" to Acc
                later.isNotEmpty() -> "⏰ الموبايل مفتوح لحد ${fmtClock(later.minOf { it.startMin })}" to Mute
                else -> "مفيش مهام النهارده 🎈" to Mute
            }
            Panel2 { Text(msg, color = col, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        item {
            OutlinedButton(onClick = { showAdd = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("➕ أضيف مهمة لنفسي")
            }
        }
        if (!usageOk) item {
            Button(onClick = { act.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("1) فعّل Usage Access") }
        }
        if (!overlayOk) item {
            Button(onClick = {
                act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${act.packageName}")))
            }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("2) فعّل Display over other apps") }
        }
        if (!batteryOk) item {
            OutlinedButton(onClick = {
                act.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${act.packageName}")))
            }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("3) اسمح بالتشغيل في الخلفية (مهم)") }
        }
        if (!accessOk) item {
            Button(onClick = { act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("4) فعّل ركّز في الإتاحة (عشان القفل يفضل شغال)") }
        }
        if (Store.serverUrl(act).isBlank()) item {
            Panel2 { Text("⚠️ مش مربوط بالأخ الكبير. افتح 🔑 الأخ الكبير ثم الربط بصفحة المتابعة.", color = Bad) }
        } else item {
            val ls = if (now > 0) Store.lastSync(act) else 0L
            Text(
                if (ls == 0L) "جاري أول مزامنة…" else "آخر مزامنة: " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ls)),
                color = Mute, fontSize = 12.sp
            )
        }
        if (todays.isEmpty()) item {
            Text("مفيش مهام النهارده. ضيف مهمة لنفسك أو استنى الأخ الكبير يضيفلك.", color = Mute)
        }
        items(todays, key = { it.id }) { t ->
            val sec = live(t)
            val isActive = activeId == t.id
            val canFinish = sec >= t.minutes * 30
            Panel2(Modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (t.done) "✅ " else "") + t.name, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${t.minutes} دقيقة", color = Mute)
                }
                if (t.startMin > 0) Text(
                    "⏰ من ${fmtClock(t.startMin)}",
                    color = if (!t.done && t.startMin > nowMin) Acc else Mute, fontSize = 13.sp
                )
                Text(fmt(sec), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (isActive) Acc else TextC)
                LinearProgressIndicator(
                    progress = { (sec / (t.minutes * 60f)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (t.done) Ok else Acc, trackColor = Bg
                )
                if (!t.done) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isActive) OutlinedButton(onClick = { Store.stopActive(act); reload() },
                            Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("⏸ إيقاف") }
                        else Button(onClick = { Store.start(act, t.id); reload() },
                            Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("▶ ابدأ") }
                        Button(onClick = {
                            if (activeId == t.id) Store.stopActive(act)
                            Store.save(act, Store.load(act).map { if (it.id == t.id) it.copy(done = true) else it })
                            Store.log(act, "done", t.name, 0, t.id)
                            reload()
                        }, modifier = Modifier.weight(1f), enabled = canFinish, shape = RoundedCornerShape(12.dp)) {
                            Text(if (canFinish) "خلصت ✅" else "بعد نص الوقت")
                        }
                    }
                }
            }
        }
        if (tasks.isNotEmpty()) item {
            val dayNames = listOf("الأحد", "الاتنين", "التلات", "الأربع", "الخميس", "الجمعة", "السبت") // 0 = الأحد
            Panel2 {
                Text("📅 جدول الأسبوع", fontWeight = FontWeight.Bold)
                listOf(6, 0, 1, 2, 3, 4, 5).forEach { d ->
                    val ts = tasks.filter { ((it.days shr d) and 1) == 1 }.sortedBy { it.startMin }
                    if (ts.isNotEmpty()) Text(
                        dayNames[d] + ": " + ts.joinToString("، ") {
                            it.name + (if (it.startMin > 0) " (${fmtClock(it.startMin)})" else "")
                        },
                        color = if (d == Store.todayDow()) Acc else Mute, fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (adminMode == 1) PinDialog(act, onOk = { adminMode = 2 }, onDismiss = { adminMode = 0 })

    if (showAdd) AddTaskDialog(
        onAdd = { name, mins ->
            Store.save(act, Store.load(act) + StudyTask(System.currentTimeMillis(), name, mins, pending = true,
                days = Store.todayBit(), once = true, day = Store.todayStamp()))
            showAdd = false
            reload()
            Thread { Sync.run(act) }.start()
        },
        onDismiss = { showAdd = false }
    )
}

@Composable
fun AddTaskDialog(onAdd: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("30") }
    val m = mins.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(enabled = name.isNotBlank() && m in 10..240, onClick = { onAdd(name.trim(), m) }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        title = { Text("➕ مهمة لنفسي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) },
                    label = { Text("المادة (مثلاً: فيزياء)") }, singleLine = true)
                OutlinedTextField(mins, { mins = it.filter(Char::isDigit).take(3) },
                    label = { Text("الدقايق (من 10 لـ 240)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    )
}

@Composable
fun PinDialog(act: MainActivity, onOk: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { if (pin == Store.pin(act)) onOk() else { err = true; pin = "" } }) { Text("دخول") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        title = { Text("🔑 الأخ الكبير") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit); err = false },
                    label = { Text("الرقم السري") }, singleLine = true, isError = err,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (err) Text("الرقم السري غلط", color = Bad)
            }
        }
    )
}

@Composable
fun AdminPage(act: MainActivity, onChange: () -> Unit, onBack: () -> Unit) {
    var rev by remember { mutableIntStateOf(0) }
    val tasks = remember(rev) { Store.todays(act) }
    val logs = remember(rev) { Store.logs(act) }
    val remoteFree = remember(rev) { Store.isFreeRemote(act) }
    val localFree = remember(rev) { Store.isFree(act) && !Store.isFreeRemote(act) }
    var blocked by remember { mutableStateOf(Store.blocked(act)) }
    var showApps by remember { mutableStateOf(false) }
    val apps = remember { loadApps(act) }
    var newPin by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(Store.serverUrl(act)) }
    var key by remember { mutableStateOf(Store.deviceKey(act)) }
    var linkMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun changed() { rev++; onChange() }

    val dayStart = remember(rev) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todaySec = logs.filter { it.type == "stop" && it.t >= dayStart }.sumOf { it.extra }
    val todayBlocked = logs.count { it.type == "blocked" && it.t >= dayStart }
    val dateFmt = remember { SimpleDateFormat("d/M HH:mm", Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("رجوع", color = Acc) }
                Text("🔑 الأخ الكبير", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            Panel2 {
                Text("المهام والمتابعة بتتحكم فيها من صفحة الأخ الكبير على موبايلك.", color = Mute, fontSize = 13.sp)
                if (remoteFree) Text("🔓 القفل ملغي من صفحة المتابعة", color = Ok, fontWeight = FontWeight.Bold)
                else {
                    Text(if (localFree) "🔓 القفل ملغي (من هنا)" else "🔒 القفل شغال",
                        color = if (localFree) Ok else Acc, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        Store.log(act, if (localFree) "relock" else "free")
                        Store.setFree(act, !localFree); changed()
                    }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(if (localFree) "🔒 رجّع القفل" else "🔓 إلغاء القفل من هنا")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(fmt(todaySec), "مذاكرة النهارده", Modifier.weight(1f))
                Stat("${tasks.count { it.done }}/${tasks.size}", "مهام خلصت", Modifier.weight(1f))
                Stat("$todayBlocked", "محاولات ممنوعة", Modifier.weight(1f))
            }
        }
        item {
            Panel2 {
                Text("الربط بصفحة المتابعة", fontWeight = FontWeight.Bold)
                OutlinedTextField(url, { url = it }, label = { Text("رابط السيرفر") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text("مفتاح الجهاز (DEVICE_KEY)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    Store.setLink(act, url, key)
                    linkMsg = "جاري الاتصال…"
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Sync.run(act) }
                        linkMsg = if (ok) "✅ اتربط واتزامن" else "❌ فشل الاتصال، راجع الرابط والمفتاح"
                        changed()
                    }
                }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("حفظ ومزامنة الآن") }
                if (linkMsg.isNotEmpty()) Text(linkMsg)
            }
        }
        item { Text("المهام الحالية", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (tasks.isEmpty()) item { Text("مفيش مهام. ضيفها من صفحة المتابعة.", color = Mute) }
        items(tasks, key = { it.id }) { t ->
            Panel2 {
                Text((if (t.done) "✅ " else "") + t.name, fontWeight = FontWeight.Bold)
                val ses = logs.count { it.type == "start" && it.id == t.id }
                Text("${fmt(t.studiedSec)} من ${t.minutes} د · $ses جلسة", color = Mute, fontSize = 13.sp)
            }
        }
        item {
            Panel2 {
                Text("التطبيقات الممنوعة (${blocked.size})", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { showApps = !showApps }, Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) { Text(if (showApps) "إخفاء القايمة" else "اختيار التطبيقات") }
            }
        }
        if (showApps) items(apps, key = { it.first }) { (pkg, label) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f))
                Switch(checked = pkg in blocked, onCheckedChange = { on ->
                    val n = if (on) blocked + pkg else blocked - pkg
                    blocked = n; Store.setBlocked(act, n)
                })
            }
        }
        item { Text("سجل النشاط (على الموبايل)", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (logs.isEmpty()) item { Text("مفيش نشاط لسه", color = Mute) }
        items(logs.takeLast(40).reversed()) { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(dateFmt.format(Date(e.t)), color = Mute, fontSize = 12.sp)
                Text(logText(e), fontSize = 14.sp)
            }
        }
        item {
            Panel2 {
                Text("تغيير الرقم السري", fontWeight = FontWeight.Bold)
                OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit) },
                    label = { Text("رقم جديد (4 أرقام+)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { if (newPin.length >= 4) { Store.setPin(act, newPin); newPin = "" } },
                    shape = RoundedCornerShape(12.dp)) { Text("حفظ الرقم") }
            }
        }
    }
}

fun fmtClock(min: Int): String {
    val h = min / 60
    val m = min % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.getDefault(), "%d:%02d %s", h12, m, if (h < 12) "ص" else "م")
}
