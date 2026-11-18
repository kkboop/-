package com.loan.island

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandStore.offsetDp.value = prefs().getInt("offset", 2)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen(tick) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    private fun prefs() = getSharedPreferences("island", MODE_PRIVATE)

    private fun go(i: Intent) { runCatching { startActivity(i) } }

    private fun setOffset(v: Int) {
        val c = v.coerceIn(-30, 60)
        IslandStore.offsetDp.value = c
        prefs().edit().putInt("offset", c).apply()
    }

    @Composable
    private fun Screen(tick: Int) {
        val overlayOk = remember(tick) { Settings.canDrawOverlays(this@MainActivity) }
        val listenerOk = remember(tick) {
            NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)
        }
        val offset by IslandStore.offsetDp.collectAsState()

        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Dynamic Island", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Faça os passos na ordem. Depois de tudo ativo, a ilha aparece sozinha quando chegar notificação, música, chamada, timer ou download.",
                    fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                Step(
                    null, "1. Liberar configurações restritas",
                    "App instalado por APK precisa disso: Info do app → ⋮ (canto superior) → Permitir configurações restritas.",
                    "Abrir info do app",
                ) { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }

                Step(
                    overlayOk, "2. Exibir sobre outros apps",
                    "Permite desenhar a ilha por cima de qualquer tela.", "Permitir sobreposição",
                ) { go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }

                Step(
                    listenerOk, "3. Acesso a notificações",
                    "Permite ler notificações, música, chamadas, timers e downloads.", "Abrir acesso a notificações",
                ) { go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

                Step(
                    null, "4. Bateria sem restrição",
                    "Coloque o app como \"Sem restrições\" e tire de \"apps em suspensão\" para a One UI não matar o serviço.",
                    "Abrir ajustes de bateria",
                ) { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }

                Text("Altura da ilha", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                Text("Ajuste até a pílula cobrir exatamente o furinho da câmera.", fontSize = 13.sp, color = Color.Gray)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { setOffset(offset - 1) }) { Text("−") }
                    Text("$offset dp", fontSize = 16.sp)
                    OutlinedButton(onClick = { setOffset(offset + 1) }) { Text("+") }
                }

                Text("Testes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
                Text("Só funcionam com os passos 2 e 3 ativos.", fontSize = 13.sp, color = Color.Gray)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { testNotification() }) { Text("Aviso") }
                    Button(onClick = { testMusic() }) { Text("Música") }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { testCall() }) { Text("Chamada") }
                    Button(onClick = { testDownload() }) { Text("Download") }
                    OutlinedButton(onClick = { clearTests() }) { Text("Limpar") }
                }
            }
        }
    }

    @Composable
    private fun Step(ok: Boolean?, title: String, desc: String, btn: String, onClick: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val mark = when (ok) { true -> "✅ "; false -> "⬜ "; null -> "" }
            Text(mark + title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 13.sp, color = Color.Gray)
            Button(onClick = onClick, modifier = Modifier.padding(top = 6.dp)) { Text(btn) }
        }
    }

    private fun testNotification() = IslandStore.showAlert(
        IslandActivity("t-n", "test", Kind.NOTIFICATION, "WhatsApp", "Oi! Bora jogar hoje à noite?")
    )

    private fun testMusic() = IslandStore.upsert(
        IslandActivity("t-m", "test", Kind.MEDIA, "Blinding Lights", "The Weeknd", playing = true),
        alert = true,
    )

    private fun testCall() = IslandStore.upsert(
        IslandActivity(
            "t-c", "test", Kind.CALL, "Mãe", "Chamada recebida",
            actions = listOf(IslandAction("Recusar", null), IslandAction("Atender", null)),
        ),
        alert = true,
    )

    private fun testDownload() = IslandStore.upsert(
        IslandActivity("t-p", "test", Kind.PROGRESS, "Baixando arquivo", "42%", progress = 42),
        alert = false,
    )

    private fun clearTests() {
        listOf("t-n", "t-m", "t-c", "t-p").forEach { IslandStore.remove(it) }
    }
}
