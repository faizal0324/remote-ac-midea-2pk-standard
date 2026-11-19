package id.midea.remote

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0E151C)
private val Panel = Color(0xFF1A2530)
private val Mist = Color(0xFFE8F0F6)
private val Dim = Color(0xFF8A9BAA)
private val OffGrey = Color(0xFF55646F)

private fun accentFor(s: MideaIr.State): Color =
    if (!s.power) OffGrey else when (s.mode) {
        MideaIr.Mode.COOL -> Color(0xFF7CCBFF)
        MideaIr.Mode.DRY -> Color(0xFF7DE0C3)
        MideaIr.Mode.AUTO -> Color(0xFFFFD27D)
        MideaIr.Mode.FAN -> Color(0xFFC4B5FF)
    }

private fun modeLabel(m: MideaIr.Mode) = when (m) {
    MideaIr.Mode.COOL -> "Dingin"
    MideaIr.Mode.DRY -> "Kering"
    MideaIr.Mode.AUTO -> "Otomatis"
    MideaIr.Mode.FAN -> "Kipas"
}

private fun timerLabel(min: Int): String = when {
    min <= 0 -> "Tidak aktif"
    min < 60 -> "$min menit"
    min % 60 == 0 -> "${min / 60} jam"
    else -> "${min / 60} jam ${min % 60} menit"
}

private class Feature(val key: String, val label: String, val code: Long, val offCode: Long? = null)

private val features = listOf(
    Feature("swing", "Ayun", MideaIr.SWING),
    Feature("turbo", "Turbo", MideaIr.TURBO),
    Feature("eco", "Eco", MideaIr.ECO),
    Feature("sleep", "Tidur", 0L),
    Feature("light", "Lampu", MideaIr.LIGHT),
    Feature("quiet", "Senyap", MideaIr.QUIET_ON, MideaIr.QUIET_OFF),
    Feature("clean", "Bersih", MideaIr.SELF_CLEAN)
)

private fun loadState(p: SharedPreferences) = MideaIr.State(
    power = p.getBoolean("power", false),
    mode = MideaIr.Mode.values().getOrElse(p.getInt("mode", 0)) { MideaIr.Mode.COOL },
    fan = MideaIr.Fan.values().getOrElse(p.getInt("fan", 0)) { MideaIr.Fan.AUTO },
    tempC = p.getInt("temp", 25).coerceIn(17, 30),
    sleep = p.getBoolean("sleep", false),
    offTimerMin = p.getInt("timer", 0)
)

private fun saveState(p: SharedPreferences, s: MideaIr.State) {
    p.edit()
        .putBoolean("power", s.power)
        .putInt("mode", s.mode.ordinal)
        .putInt("fan", s.fan.ordinal)
        .putInt("temp", s.tempC)
        .putBoolean("sleep", s.sleep)
        .putInt("timer", s.offTimerMin)
        .apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sender = IrSender(this)
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        setContent { RemoteApp(sender, prefs) }
    }
}

@Composable
fun RemoteApp(sender: IrSender, prefs: SharedPreferences) {
    var st by remember { mutableStateOf(loadState(prefs)) }
    var flags by remember { mutableStateOf<Set<String>>(prefs.getStringSet("flags", emptySet()) ?: emptySet()) }
    var info by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val accent = accentFor(st)

    fun report(ok: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        info = if (ok) "Sinyal terkirim" else "Gagal mengirim sinyal IR"
    }

    fun push(next: MideaIr.State) {
        st = next
        saveState(prefs, next)
        report(sender.send(MideaIr.commandBytes(next)))
    }

    // Seperti remote asli: saat AC mati, pengaturan hanya diubah di layar, belum dikirim.
    fun change(next: MideaIr.State) {
        if (st.power) push(next) else {
            st = next
            saveState(prefs, next)
        }
    }

    fun toggleFeature(f: Feature) {
        if (!st.power) {
            info = "Nyalakan AC dulu"
            return
        }
        if (f.key == "sleep") {
            push(st.copy(sleep = !st.sleep))
            return
        }
        val wasOn = f.key in flags
        flags = if (wasOn) flags - f.key else flags + f.key
        prefs.edit().putStringSet("flags", flags).apply()
        val code = if (wasOn && f.offCode != null) f.offCode else f.code
        report(sender.send(MideaIr.fromLong(code)))
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = Ink, surface = Ink)) {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Midea 2 PK", color = Mist, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("MSAF-18CRN2X", color = Dim, fontSize = 12.sp)
                    }
                    Text(info, color = Dim, fontSize = 12.sp)
                }

                if (!sender.available) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "HP ini tidak mendeteksi pemancar IR, jadi sinyal tidak bisa dikirim ke AC.",
                        color = Color(0xFFFFB4A8), fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(28.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundBtn("−", 64.dp) { if (st.tempC > 17) change(st.copy(tempC = st.tempC - 1)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${st.tempC}°", color = accent, fontSize = 104.sp, fontWeight = FontWeight.Light)
                        Text(
                            if (st.power) modeLabel(st.mode) else "AC mati",
                            color = Dim, fontSize = 14.sp
                        )
                    }
                    RoundBtn("+", 64.dp) { if (st.tempC < 30) change(st.copy(tempC = st.tempC + 1)) }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(if (st.power) accent.copy(alpha = 0.18f) else Panel)
                        .border(3.dp, accent, CircleShape)
                        .clickable { push(st.copy(power = !st.power)) },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(34.dp)) {
                        val sw = 4.dp.toPx()
                        val w = size.width
                        drawArc(
                            color = accent, startAngle = -50f, sweepAngle = 280f, useCenter = false,
                            topLeft = Offset(sw, sw * 1.5f), size = Size(w - 2 * sw, w - 2 * sw - sw * 0.5f),
                            style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                        drawLine(accent, Offset(w / 2, sw / 2), Offset(w / 2, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
                    }
                }

                Spacer(Modifier.height(28.dp))

                SectionLabel("Mode")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MideaIr.Mode.COOL, MideaIr.Mode.DRY, MideaIr.Mode.AUTO, MideaIr.Mode.FAN).forEach { m ->
                        Pill(modeLabel(m), st.mode == m, accent, Modifier.weight(1f)) { change(st.copy(mode = m)) }
                    }
                }

                Spacer(Modifier.height(18.dp))

                SectionLabel("Kecepatan kipas")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        MideaIr.Fan.AUTO to "Auto", MideaIr.Fan.LOW to "Rendah",
                        MideaIr.Fan.MED to "Sedang", MideaIr.Fan.HIGH to "Tinggi"
                    ).forEach { (f, label) ->
                        Pill(label, st.fan == f, accent, Modifier.weight(1f)) { change(st.copy(fan = f)) }
                    }
                }

                Spacer(Modifier.height(18.dp))

                SectionLabel("Fitur")
                features.chunked(4).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { f ->
                            val selected = if (f.key == "sleep") st.sleep else f.key in flags
                            Pill(f.label, selected && st.power, accent, Modifier.weight(1f)) { toggleFeature(f) }
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Panel)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Timer mati", color = Mist, fontSize = 15.sp)
                        Text(timerLabel(st.offTimerMin), color = if (st.offTimerMin > 0) accent else Dim, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundBtn("−", 44.dp) { change(st.copy(offTimerMin = (st.offTimerMin - 30).coerceAtLeast(0))) }
                        Spacer(Modifier.width(10.dp))
                        RoundBtn("+", 44.dp) { change(st.copy(offTimerMin = (st.offTimerMin + 30).coerceAtMost(1440))) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Status tombol fitur hanya perkiraan. Jika tidak sama dengan AC, ketuk sekali lagi.",
                    color = Dim, fontSize = 11.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = Dim, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
private fun Pill(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent else Panel)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Ink else Mist,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun RoundBtn(label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Panel).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Mist, fontSize = (size.value * 0.42f).sp)
    }
}
