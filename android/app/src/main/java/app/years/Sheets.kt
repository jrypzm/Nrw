@file:OptIn(ExperimentalMaterial3Api::class)

package app.years

import android.Manifest
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

// ------------------------------------------------------------------ shared bits

@Composable
fun SheetHandle() {
    Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(C.Faint))
}

@Composable
fun Caps(t: String) { Text(t.uppercase(), style = T.caps, modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)) }

@Composable
fun Chevron() { Text("›", style = T.body.copy(color = C.Faint, fontSize = 22.sp)) }

@Composable
fun YrsSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = C.Surface, scrimColor = Color.Black.copy(alpha = .6f), dragHandle = { SheetHandle() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(), content = content)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, max: Int, lines: Int = 1, focus: FocusRequester? = null, onDone: (() -> Unit)? = null) {
    BasicTextField(
        value = value, onValueChange = { onChange(it.take(max)) }, singleLine = lines == 1, minLines = lines,
        textStyle = T.body.copy(lineHeight = 23.sp), cursorBrush = SolidColor(C.Fg),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (lines == 1) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).clip(RoundedCornerShape(12.dp)).background(C.Surface2).padding(16.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = T.body.copy(color = C.Faint)); inner() } },
    )
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsSheet(st: AppState, onDismiss: () -> Unit, toast: (String) -> Unit, openLifespan: () -> Unit) {
    val ctx = LocalContext.current
    val s = st.s
    var editBirth by remember { mutableStateOf(false) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        st.update(refresh = false) { it.copy(nudge = ok) }; Nudge.schedule(ctx, ok)
        if (!ok) toast("Notifications are off for yrs.")
    }
    YrsSheet(onDismiss) {
        Text("Settings", style = T.h2, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))

        Caps("You")
        Group {
            val b = s.birth ?: LocalDate.now()
            SettingRow("Birthday", onClick = { editBirth = !editBirth }) {
                Text("${b.dayOfMonth} ${Life.mon(b)} ${b.year}", style = T.body.copy(color = if (editBirth) C.Fg else C.Mute))
            }
            AnimatedVisibility(editBirth) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { DateWheels(b, { d -> st.update { it.copy(birth = d) } }) }
            }
            Divider()
            SettingRow("Lifespan") { Stepper(s.span, { v -> st.update { it.copy(span = v) } }, 40..120) }
            Divider()
            SettingRow("Estimate my lifespan", "Five honest questions", onClick = openLifespan) { Chevron() }
        }

        Caps("Number")
        Segmented(NumberStyle.entries, s.style, { when (it) { NumberStyle.CLASSIC -> "Classic"; NumberStyle.HERO -> "Large"; NumberStyle.LIVE -> "Live" } }) { v -> st.update(refresh = false) { it.copy(style = v) } }

        Caps("Feel")
        Group {
            SettingRow("Daylight", "Dots glow by day and dim at night") { Toggle(s.sun) { v -> st.update { it.copy(sun = v) } } }
            Divider()
            SettingRow("Ripple", "A wave runs through your years each time you come back") { Toggle(s.ripple) { v -> st.update(refresh = false) { it.copy(ripple = v) } } }
            Divider()
            SettingRow("Monday note", "“Week 1,812 begins.” Once a week, nothing else") {
                Toggle(s.nudge) { v ->
                    if (v && !Nudge.permitted(ctx) && Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { st.update(refresh = false) { it.copy(nudge = v) }; Nudge.schedule(ctx, v) }
                }
            }
        }

        Caps("Lock screen")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WallStyle.entries.forEach { ws ->
                val sel = ws == s.wall
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        Modifier.fillMaxWidth().aspectRatio(9f / 19.5f).clip(RoundedCornerShape(14.dp))
                            .border(if (sel) 2.dp else 1.dp, if (sel) C.Fg else C.Sep, RoundedCornerShape(14.dp))
                            .pressable({ st.update(forceLock = true) { it.copy(wall = ws) } }),
                    ) {
                        drawIntoCanvas { Render.wallpaper(ctx, it.nativeCanvas, size.width.toInt(), size.height.toInt(), s, ws, caption = false) }
                    }
                    Text(ws.label, style = T.small.copy(color = if (sel) C.Fg else C.Mute), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Group {
            SettingRow("Set live wallpaper", "Ripples when you wake your phone, rests otherwise", onClick = { setLiveWallpaper(ctx) }) { Chevron() }
            Divider()
            SettingRow("Auto-update lock screen", "Repaints your lock screen every hour, on any phone") {
                Toggle(s.autoLock) { v -> st.update(forceLock = v) { it.copy(autoLock = v) }; if (v) toast("Lock screen updating") }
            }
            Divider()
            SettingRow("Save as image", onClick = { if (saveWallpaperImage(ctx, s) != null) toast("Saved to Pictures/yrs") else toast("Couldn't save the image") }) { Chevron() }
        }

        Caps("Widgets")
        Segmented(WidgetLook.entries, s.widgetLook, { it.label }) { v -> st.update { it.copy(widgetLook = v) } }
        Spacer(Modifier.height(14.dp))
        Group {
            val list = listOf(
                "Life" to LifeWidgetReceiver::class.java, "Years left" to NumberWidgetReceiver::class.java,
                "Days left" to DaysWidgetReceiver::class.java, "Today" to TodayWidgetReceiver::class.java,
                "People" to PeopleWidgetReceiver::class.java, "This decade" to DecadeWidgetReceiver::class.java,
                "This year of life" to YearWidgetReceiver::class.java,
            )
            list.forEachIndexed { i, (n, cls) ->
                if (i > 0) Divider()
                SettingRow("Add $n widget", onClick = { if (!pinWidget(ctx, cls)) toast("Long-press your home screen, then Widgets") }) { Chevron() }
            }
        }

        Caps("Privacy")
        Group {
            SettingRow("Screen time access", if (ScreenTime.allowed(ctx)) "Allowed. Used only for your Ledger" else "Off. The Ledger assumes 3 hours a day", onClick = { ScreenTime.openSettings(ctx) }) { Chevron() }
            Divider()
            SettingRow("Share your grid", onClick = { shareGrid(ctx, s) }) { Chevron() }
        }

        Text(
            "Swipe for your decade, this year, the people you love and where your years go. Pinch the life grid to zoom from years to days. Hold a dot and drag to travel through time. Tap a year to name it, or seal a letter to a future one. Tap the number to count in days, weeks, summers, weekends or full moons. Hold it to feel your heartbeat.\n\nyrs. has no internet permission. Everything stays on this phone.",
            style = T.small, modifier = Modifier.padding(top = 20.dp, start = 4.dp, end = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Done", onDismiss)
    }
}

// ------------------------------------------------------------------ name a year, seal a letter

@Composable
fun MilestoneSheet(st: AppState, year: Int, onDismiss: () -> Unit) {
    val s = st.s
    val view = LocalView.current
    val lived = s.birth?.let { Life.age(it).toInt() } ?: 0
    val past = year <= lived
    var name by remember { mutableStateOf(s.names[year] ?: "") }
    var letter by remember { mutableStateOf("") }
    val sealed = s.letters.firstOrNull { it.age == year && !it.opened }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(350); runCatching { focus.requestFocus() } }
    fun save() {
        val n = name.trim().take(40)
        val l = letter.trim()
        st.update {
            var x = it.copy(names = if (n.isEmpty()) it.names - year else it.names + (year to n))
            if (!past && l.isNotEmpty()) x = x.copy(letters = x.letters.filter { lt -> lt.age != year || lt.opened } + Letter(year, l, LocalDate.now()))
            x
        }
        view.confirm(); onDismiss()
    }
    YrsSheet(onDismiss) {
        Text("Age $year · ${(s.birth?.year ?: 0) + year}", style = T.h2, modifier = Modifier.padding(top = 6.dp))
        Text(if (past) "What happened this year?" else "What do you want this year to hold?", style = T.sub, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Field(name, { name = it }, if (past) "Moved to a new city" else "See the northern lights", 40, focus = focus, onDone = { save() })
        Text("Named years get a thin ring.", style = T.small, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        if (!past) {
            Caps("A letter to yourself at $year")
            if (sealed != null) Text("A letter is sealed for this year. Writing a new one replaces it.", style = T.small, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            Field(letter, { letter = it }, "Dear me at $year…", 2000, lines = 5)
            Text("It stays sealed until your birthday that year. Not even you can read it before then.", style = T.small, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save", { save() })
        if (s.names.containsKey(year)) QuietButton("Remove name", { name = ""; save() })
    }
}

@Composable
fun LetterSheet(st: AppState, index: Int, onDismiss: () -> Unit) {
    val l = st.s.letters.getOrNull(index) ?: run { onDismiss(); return }
    fun keep() { st.update(refresh = false) { it.copy(letters = it.letters.mapIndexed { i, x -> if (i == index) x.copy(opened = true) else x }) }; onDismiss() }
    YrsSheet({ keep() }) {
        Text("A letter from you", style = T.h2, modifier = Modifier.padding(top = 6.dp))
        Text("Written ${l.written.dayOfMonth} ${Life.mon(l.written)} ${l.written.year}, for you at ${l.age}", style = T.sub, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
        Text(l.text, style = T.body.copy(fontSize = 18.sp, lineHeight = 27.sp))
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Keep it", { keep() })
    }
}

// ------------------------------------------------------------------ people

@Composable
fun PersonSheet(st: AppState, index: Int?, onDismiss: () -> Unit) {
    val existing = index?.let { st.s.people.getOrNull(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var year by remember { mutableIntStateOf(existing?.birthYear ?: 1965) }
    var often by remember { mutableStateOf(existing?.often ?: Often.SEASONAL) }
    val years = remember { (1900..LocalDate.now().year).toList() }
    fun save() {
        val n = name.trim().take(24)
        if (n.isEmpty()) { onDismiss(); return }
        val p = Person(n, year, often)
        st.update { it.copy(people = if (index == null) it.people + p else it.people.mapIndexed { i, x -> if (i == index) p else x }) }
        onDismiss()
    }
    YrsSheet(onDismiss) {
        Text(if (existing == null) "Add someone" else existing.name, style = T.h2, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Field(name, { name = it }, "Mum", 24)
        Caps("Born in")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2))
            Wheel(years.map { "$it" }, years.indexOf(year).coerceAtLeast(0), { year = years[it] }, 120.dp)
        }
        Caps("You see each other")
        Group {
            Often.entries.forEachIndexed { i, o ->
                if (i > 0) Divider()
                SettingRow(o.label, onClick = { often = o }) { if (o == often) Text("✓", style = T.body) }
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save", { save() })
        if (index != null) QuietButton("Remove", { st.update { it.copy(people = it.people.filterIndexed { i, _ -> i != index }) }; onDismiss() })
    }
}

// ------------------------------------------------------------------ countdowns

@Composable
fun EventSheet(st: AppState, index: Int?, onDismiss: () -> Unit) {
    val existing = index?.let { st.s.events.getOrNull(it) }
    val today = LocalDate.now()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: today.plusMonths(1)) }
    fun save() {
        val n = name.trim().take(30)
        if (n.isEmpty()) { onDismiss(); return }
        val e = Event(n, date)
        st.update { it.copy(events = if (index == null) it.events + e else it.events.mapIndexed { i, x -> if (i == index) e else x }) }
        onDismiss()
    }
    YrsSheet(onDismiss) {
        Text(if (existing == null) "Add a countdown" else existing.name, style = T.h2, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Field(name, { name = it }, "Trip to Japan", 30)
        Caps("On")
        DateWheels(date, { date = it }, fromYear = today.year, toYear = today.year + 30, latest = null, earliest = today)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save", { save() })
        if (index != null) QuietButton("Remove", { st.update { it.copy(events = it.events.filterIndexed { i, _ -> i != index }) }; onDismiss() })
    }
}

// ------------------------------------------------------------------ one line a day

@Composable
fun LineSheet(st: AppState, date: LocalDate, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var text by remember { mutableStateOf(Lines.get(ctx, date) ?: "") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(350); runCatching { focus.requestFocus() } }
    val birth = st.s.birth ?: date
    fun save() { Lines.put(ctx, date, text); st.bump(); Background.refreshNow(ctx); view.confirm(); onDismiss() }
    YrsSheet(onDismiss) {
        Text("Day ${Life.whole(Life.dayOfLife(birth, date).toDouble())}", style = T.h2, modifier = Modifier.padding(top = 6.dp))
        Text("${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth} ${Life.mon(date)} ${date.year}", style = T.sub, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Field(text, { text = it }, "One line. What made today today?", 140, lines = 3, focus = focus)
        Text("${text.length}/140 · One line is enough. In ten years you'll be glad you wrote it.", style = T.small, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save", { save() })
    }
}

// ------------------------------------------------------------------ the year you just finished

@Composable
fun ReviewSheet(st: AppState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s = st.s
    val birth = s.birth ?: run { onDismiss(); return }
    val lived = Life.age(birth).toInt()
    val finished = lived - 1
    val end = Life.lastBirthday(birth, LocalDate.now())
    val start = end.minusYears(1)
    val lines = remember { Lines.all(ctx).filterKeys { k -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { !it.isBefore(start) && it.isBefore(end) } == true }.toSortedMap() }
    fun done() { st.update(refresh = false) { it.copy(reviewedAge = lived) }; onDismiss() }
    YrsSheet({ done() }) {
        Text("Age $finished, complete", style = T.h1.copy(fontSize = 30.sp), modifier = Modifier.padding(top = 6.dp))
        Text("${start.year}–${end.year} · one more white dot", style = T.sub, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        Group {
            SettingRow("Days lived") { Text(Life.whole(java.time.temporal.ChronoUnit.DAYS.between(start, end).toDouble()), style = T.body.copy(color = C.Mute)) }
            Divider()
            SettingRow("Lines written") { Text("${lines.size}", style = T.body.copy(color = C.Mute)) }
            s.names[finished]?.let { Divider(); SettingRow("You called it") { Text(it, style = T.body.copy(color = C.Mute)) } }
        }
        if (lines.isNotEmpty()) {
            Caps("First line of the year")
            Text("“${lines.values.first()}”", style = T.body.copy(fontSize = 17.sp, lineHeight = 24.sp))
            if (lines.size > 1) {
                Caps("Last line of the year")
                Text("“${lines.values.last()}”", style = T.body.copy(fontSize = 17.sp, lineHeight = 24.sp))
            }
        }
        Spacer(Modifier.height(26.dp))
        PrimaryButton("Begin year ${lived + 1}", { done() })
    }
}

// ------------------------------------------------------------------ lifespan check

@Composable
fun LifespanSheet(st: AppState, onDismiss: () -> Unit, toast: (String) -> Unit) {
    var habits by remember { mutableStateOf(st.s.habits) }
    val est = Life.estimate(habits)
    YrsSheet(onDismiss) {
        Text("Estimate my lifespan", style = T.h2, modifier = Modifier.padding(top = 6.dp))
        Text("Starting from 80, adjusted by habits with the clearest evidence.", style = T.sub, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Group {
            Life.HABITS.forEachIndexed { i, (key, label, delta) ->
                if (i > 0) Divider()
                SettingRow(label, if (delta > 0) "+$delta years" else "$delta years") {
                    Toggle(key in habits) { on -> habits = if (on) habits + key else habits - key }
                }
            }
        }
        Row(Modifier.padding(top = 26.dp), verticalAlignment = Alignment.Bottom) {
            Text("$est", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 72.sp, letterSpacing = (-0.05).em, color = C.Fg, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline())
            Text("years", style = T.sub.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 8.dp).alignByBaseline())
        }
        Text("A rough population average to reflect on, not medical advice.", style = T.small, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Use $est years", { st.update { it.copy(span = est, habits = habits) }; toast("Lifespan set to $est"); onDismiss() })
    }
}

// ------------------------------------------------------------------ system actions

fun setLiveWallpaper(ctx: Context) {
    val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, YearsWallpaperService::class.java))
    runCatching { ctx.startActivity(i) }.onFailure { runCatching { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } }
}

fun pinWidget(ctx: Context, cls: Class<*>): Boolean {
    val m = AppWidgetManager.getInstance(ctx)
    return if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(ctx, cls), null, null) else false
}

fun saveImage(ctx: Context, bmp: Bitmap, name: String): Uri? = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/yrs")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    uri
}.getOrNull()

fun saveWallpaperImage(ctx: Context, s: Settings): Uri? {
    val (w, h) = Background.screen(ctx)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Render.wallpaper(ctx, ACanvas(bmp), w, h, s, s.wall)
    return saveImage(ctx, bmp, "yrs-wallpaper")
}

fun shareGrid(ctx: Context, s: Settings) {
    val uri = saveImage(ctx, Render.shareCard(ctx, s), "yrs") ?: return
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share your years")) }
}
