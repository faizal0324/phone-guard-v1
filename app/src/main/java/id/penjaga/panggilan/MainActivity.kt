package id.penjaga.panggilan

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class FwdType(val label: String, val code: String) {
    ALL("Semua", "21"),
    BUSY("Sibuk", "67"),
    NOANS("Tak diangkat", "61"),
    UNREACH("Tak terjangkau", "62")
}

private val Mint = Color(0xFF4DE8B0)
private val Amber = Color(0xFFFFC857)
private val Grey = Color(0xFF9A93B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs: SharedPreferences = remember { ctx.getSharedPreferences("pengalih", Context.MODE_PRIVATE) }
    val rm = remember { ctx.getSystemService(RoleManager::class.java) }

    var mode by remember { mutableIntStateOf(prefs.getInt("mode", 0)) }
    var roleHeld by remember { mutableStateOf(rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) }
    var blocked by remember { mutableIntStateOf(prefs.getInt("blocked", 0)) }
    var number by remember { mutableStateOf(prefs.getString("fwd_number", "") ?: "") }
    var fwdType by remember { mutableStateOf(FwdType.ALL) }
    var msg by remember { mutableStateOf("") }

    // Perbarui hitungan panggilan terblokir secara langsung
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "blocked") blocked = p.getInt("blocked", 0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        msg = if (roleHeld) "Penjaga aktif" else "Izin penjaga belum diberikan"
    }

    fun requestRole() {
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            msg = "HP ini tidak mendukung penyaring panggilan"
            return
        }
        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) msg = "Tanpa izin kontak, tidak ada panggilan yang akan diblokir pada mode ini"
        if (!roleHeld) requestRole()
    }

    fun selectMode(m: Int) {
        mode = m
        prefs.edit().putInt("mode", m).apply()
        if (m == 0) return
        val hasContacts = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (m == 1 && !hasContacts) contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        else if (!roleHeld) requestRole()
    }

    fun dial(code: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
        } catch (e: Exception) {
            msg = "Tidak bisa membuka aplikasi telepon"
        }
    }

    val clean = number.filter { it.isDigit() || it == '+' }
    val numberValid = clean.count { it.isDigit() } >= 6

    val working = mode != 0 && roleHeld
    val statusColor = when {
        working -> Mint
        mode != 0 -> Amber
        else -> Grey
    }

    MaterialTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2E1A6B), Color(0xFF0B0816))))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))
                Text("🛡️", fontSize = 64.sp)
                Text("Penjaga Panggilan", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        mode == 0 -> "Nonaktif. Semua panggilan masuk seperti biasa."
                        mode == 1 && roleHeld -> "Aktif. Nomor di luar kontak ditolak otomatis."
                        mode == 2 && roleHeld -> "Aktif. Semua panggilan masuk ditolak."
                        else -> "Belum aktif. Izin penjaga perlu diberikan."
                    },
                    color = statusColor, fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Text("$blocked panggilan sudah diblokir", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)

                Spacer(Modifier.height(20.dp))

                Panel {
                    Text("Tolak panggilan di HP ini", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Seg("Mati", mode == 0, Modifier.weight(1f)) { selectMode(0) }
                        Seg("Tak dikenal", mode == 1, Modifier.weight(1f)) { selectMode(1) }
                        Seg("Semua", mode == 2, Modifier.weight(1f)) { selectMode(2) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tak dikenal: menolak nomor yang tidak ada di kontak dan nomor tersembunyi. " +
                            "Semua: menolak seluruh panggilan seluler, termasuk dari keluarga. " +
                            "Penelepon akan mendengar nada sibuk atau ditolak. Panggilan WhatsApp tidak terpengaruh.",
                        color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp
                    )
                    if (mode != 0 && !roleHeld) {
                        Spacer(Modifier.height(12.dp))
                        ActionBtn("Berikan izin penjaga", true, true, Modifier.fillMaxWidth()) { requestRole() }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Panel {
                    Text("Alihkan lewat operator", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Aplikasi membuka telepon dengan kode yang sudah terisi. Tekan tombol panggil untuk mengirimnya. " +
                            "Panggilan yang dialihkan ke nomor lain bisa dikenai biaya dari operator.",
                        color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FwdType.values().forEach { t ->
                            Seg(t.label, fwdType == t, Modifier.weight(1f), small = true) { fwdType = t }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("Nomor tujuan, contoh +62812xxxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Mint,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Mint,
                            focusedLabelColor = Mint,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionBtn("Aktifkan", true, numberValid, Modifier.weight(1f)) {
                            prefs.edit().putString("fwd_number", number).apply()
                            dial("**${fwdType.code}*$clean#")
                        }
                        ActionBtn("Matikan", false, true, Modifier.weight(1f)) { dial("##${fwdType.code}#") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionBtn("Cek status", false, true, Modifier.weight(1f)) { dial("*#${fwdType.code}#") }
                        ActionBtn("Hapus semua", false, true, Modifier.weight(1f)) { dial("##002#") }
                    }
                }

                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(msg, color = Amber, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Seg(label: String, selected: Boolean, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(if (small) 44.dp else 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF2E1A6B) else Color.White,
            fontSize = if (small) 11.sp else 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionBtn(label: String, filled: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val base = if (filled) Mint else Color.White.copy(alpha = 0.14f)
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) base else Color.White.copy(alpha = 0.06f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.35f)
                filled -> Color(0xFF0B0816)
                else -> Color.White
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
