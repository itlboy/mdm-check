package com.itlboy.mdmcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** UI strings follow the phone language (see [Texts]). */
private val tx: Texts get() = Texts.current()

private val HeaderHeight = 48.dp
private val ScreenPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp)

private val Bg = Color(0xFFF4F6F9)
private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF4B5563)
private val Faint = Color(0xFF9CA3AF)
private val Line = Color(0xFFF1F2F4)
private val Blue = Color(0xFF2563EB)

private data class Tone(val fg: Color, val bg: Color, val solid: Color)

private val Good = Tone(Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF16A34A))
private val Bad = Tone(Color(0xFFB91C1C), Color(0xFFFEE2E2), Color(0xFFDC2626))
private val Warn = Tone(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFFD97706))
private val Plain = Tone(Ink, Color(0xFFF3F4F6), Ink)

private fun tone(s: Status) = when (s) {
    Status.OK -> Good
    Status.BAD -> Bad
    Status.WARN -> Warn
    Status.INFO -> Plain
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Bg, surface = Color.White)) {
                App()
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Result : Screen
    data class Detail(val index: Int) : Screen
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val device = remember { Collector.summary(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var report by remember { mutableStateOf<Report?>(null) }
    var running by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<Latest?>(null) }
    var updateChecked by remember { mutableStateOf(false) }
    val version = VersionState(BuildConfig.VERSION_NAME, latest, updateChecked)

    LaunchedEffect(Unit) {
        latest = Updater.latest()
        updateChecked = true
    }

    LaunchedEffect(running) {
        if (running) {
            val r = withContext(Dispatchers.Default) { Collector.run(ctx) }
            delay(500) // let the user see that a scan happened
            report = r
            running = false
            screen = Screen.Result
        }
    }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen is Screen.Detail) Screen.Result else Screen.Home
    }

    Box(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val r = report
        when (val s = screen) {
            Screen.Home -> HomeScreen(device, version, running) { running = true }
            Screen.Result -> if (r != null) ResultScreen(device, version, r,
                onOpen = { screen = Screen.Detail(it) },
                onAgain = { screen = Screen.Home; running = true })
            is Screen.Detail -> if (r != null) DetailScreen(device, r.sections[s.index]) { screen = Screen.Result }
        }
    }
}

@Composable
private fun Header(onBack: (() -> Unit)? = null) {
    // Fixed height (same as the back button) and the same screen padding everywhere, so the title never moves.
    Box(Modifier.fillMaxWidth().height(HeaderHeight)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Ic.back, contentDescription = tx.back, tint = Ink, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            "Cozy MDM Check", modifier = Modifier.align(Alignment.Center),
            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-0.5).sp,
        )
    }
}

@Composable
private fun DeviceLine(d: DeviceSummary) {
    // One line, small enough for long names ("OPPO Find X9 Ultra · Set up 21/10/2026"); never wraps.
    val text = d.name + (d.setup?.let { " · " + tx.lineSetup(Rules.fmtDate(it, tx)) } ?: "")
    Text(text, Modifier.padding(top = 6.dp), fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(d: DeviceSummary, v: VersionState, running: Boolean, onCheck: () -> Unit) {
    var info by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header()
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${d.name} · Android ${d.android}", fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                color = Ink, textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.setup?.let { tx.homeSetup(Rules.fmtDate(it, tx)) } ?: tx.homeSetupUnknown,
                    fontSize = 16.sp, color = Muted,
                )
                IconButton(onClick = { info = true }) {
                    Icon(Ic.info, contentDescription = tx.infoTitle, tint = Blue,
                        modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            BigButton(running, onCheck)
        }
        VersionPill(v)
    }
    if (info) {
        ModalBottomSheet(onDismissRequest = { info = false }, containerColor = Color.White) {
            Column(
                Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(tx.infoTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text(
                    tx.infoBody1,
                    fontSize = 17.sp, lineHeight = 25.sp, color = Color(0xFF374151),
                )
                Text(
                    tx.infoBody2,
                    fontSize = 17.sp, lineHeight = 25.sp, color = Color(0xFF374151),
                )
                PrimaryButton(tx.gotIt) { info = false }
            }
        }
    }
}

@Composable
private fun BigButton(running: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(252.dp).clip(CircleShape).background(Blue.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(220.dp).clip(CircleShape).background(Blue)
                .clickable(enabled = !running, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (running) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.shieldCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(tx.check, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White),
    ) { content() }
}

@Composable
private fun ResultScreen(d: DeviceSummary, v: VersionState, r: Report, onOpen: (Int) -> Unit, onAgain: () -> Unit) {
    // No scrolling: everything fits one screen; flexible gaps push the button to the bottom.
    val t = tone(r.status)
    Column(
        Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header()
        DeviceLine(d)
        Spacer(Modifier.weight(0.6f))
        Box(
            Modifier.size(100.dp).clip(CircleShape).background(t.solid.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(t.solid), contentAlignment = Alignment.Center) {
                Icon(if (r.status == Status.OK) Ic.check else if (r.status == Status.BAD) Ic.x else Ic.alert,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(r.headline, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center)
        Text(r.sub, Modifier.padding(top = 4.dp), fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(0.6f))
        Card {
            r.sections.forEachIndexed { i, s ->
                MenuRow(s) { onOpen(i) }
                if (i < r.sections.lastIndex) HorizontalDivider(color = Line)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(tx.checkAgain, onAgain)
    }
}

data class VersionState(val current: String, val latest: Latest?, val checked: Boolean)

/** Green: up to date. Red: newer release (tap to open the download page). Grey: checking or offline. */
@Composable
private fun VersionPill(v: VersionState) {
    val uri = LocalUriHandler.current
    val newer = v.latest != null && Rules.isNewer(v.current, v.latest.version)
    val (dot, text) = when {
        // Kept short so the pill always fits on one line.
        !v.checked -> Faint to tx.pillChecking(v.current)
        v.latest == null -> Faint to tx.pillOffline(v.current)
        newer -> Bad.solid to tx.pillUpdate(v.current, v.latest?.version.orEmpty())
        else -> Good.solid to tx.pillUpToDate(v.current)
    }
    val base = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
    val mod = if (newer) base.clickable(role = Role.Button) { uri.openUri(v.latest!!.url) } else base
    Row(mod.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.size(10.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (newer) Bad.fg else Muted,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (newer) {
            Spacer(Modifier.size(6.dp))
            Icon(Ic.chevron, contentDescription = null, tint = Bad.fg, modifier = Modifier.size(16.dp))
        }
    }
}

private fun sectionIcon(k: Kind): ImageVector = when (k) {
    Kind.MDM -> Ic.shield
    Kind.DEVICE -> Ic.phone
    Kind.HISTORY -> Ic.clock
    Kind.SECURITY -> Ic.lock
}

private fun sectionChip(s: Section): Tone = when (s.kind) {
    Kind.MDM -> tone(s.status)
    Kind.DEVICE -> Tone(Color(0xFF1D4ED8), Color(0xFFDBEAFE), Blue)
    Kind.HISTORY -> Warn
    Kind.SECURITY -> Tone(Color(0xFF6D28D9), Color(0xFFEDE9FE), Color(0xFF6D28D9))
}

@Composable
private fun MenuRow(s: Section, onClick: () -> Unit) {
    val chip = sectionChip(s)
    val subColor = when {
        s.kind == Kind.MDM -> tone(s.status).fg
        s.status == Status.WARN -> Warn.fg
        else -> Muted
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(chip.bg), contentAlignment = Alignment.Center) {
            Icon(sectionIcon(s.kind), contentDescription = null, tint = chip.fg, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(s.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(s.summary, fontSize = 15.sp, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Ic.chevron, contentDescription = null, tint = Faint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DetailScreen(d: DeviceSummary, s: Section, onBack: () -> Unit) {
    // No scrolling: compact one-line section header, each row at most two lines of detail.
    val chip = sectionChip(s)
    Column(
        Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(onBack)
        DeviceLine(d)
        Spacer(Modifier.weight(0.5f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(chip.bg), contentAlignment = Alignment.Center) {
                Icon(sectionIcon(s.kind), contentDescription = null, tint = chip.fg, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text(s.summary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = if (s.kind == Kind.MDM) tone(s.status).fg else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(0.5f))
        Card {
            s.rows.forEachIndexed { i, row ->
                DetailRow(row)
                if (i < s.rows.lastIndex) HorizontalDivider(color = Line)
            }
        }
        Spacer(Modifier.weight(1f))
        if (s.kind == Kind.MDM) {
            Text(
                tx.zeroTouchNote,
                Modifier.padding(horizontal = 8.dp),
                fontSize = 14.sp, lineHeight = 20.sp, color = Muted, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DetailRow(row: CheckRow) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(row.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            if (row.detail.isNotBlank()) Text(row.detail, fontSize = 14.sp, color = Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val t = tone(row.status)
        when {
            row.value != null -> Text(
                row.value, Modifier.widthIn(max = 160.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = t.fg, textAlign = TextAlign.End,
            )
            row.status != Status.INFO -> Box(
                Modifier.size(28.dp).clip(CircleShape).background(t.bg), contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (row.status) { Status.OK -> Ic.check; Status.BAD -> Ic.x; else -> Ic.alert },
                    contentDescription = when (row.status) { Status.OK -> tx.badgeClear; Status.BAD -> tx.badgeIssue; else -> tx.badgeReview },
                    tint = t.fg, modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
