package de.marween.tv.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.marween.tv.AppViewModel
import de.marween.tv.Store

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var ua by remember { mutableStateOf(vm.userAgent) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Row(
            Modifier.fillMaxWidth()
                .focusHighlight()
                .toggleable(value = vm.hideAdult, onValueChange = { vm.updateHideAdult(it) })
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Erwachseneninhalte ausblenden", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Blendet Gruppen und Sender wie „XXX“ oder „Adult“ aus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = vm.hideAdult, onCheckedChange = null)
        }

        HorizontalDivider()
        Text("Live-Format bei Xtream", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = vm.liveFormat == "ts",
                onClick = { vm.updateLiveFormat("ts") },
                label = { Text("MPEG-TS (.ts)") },
                modifier = Modifier.focusHighlight(),
            )
            FilterChip(
                selected = vm.liveFormat == "m3u8",
                onClick = { vm.updateLiveFormat("m3u8") },
                label = { Text("HLS (.m3u8)") },
                modifier = Modifier.focusHighlight(),
            )
        }
        Text(
            "Wechsle das Format, falls Live-Sender nicht starten oder ruckeln.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        Text("User-Agent", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = ua, onValueChange = { ua = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            Button(onClick = {
                vm.updateUserAgent(ua)
                Toast.makeText(context, "Gespeichert", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.focusHighlight()) { Text("Speichern") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                ua = Store.DEFAULT_UA
                vm.updateUserAgent(Store.DEFAULT_UA)
            }, modifier = Modifier.focusHighlight()) { Text("Standard") }
        }
        Text(
            "Manche Anbieter erlauben nur bestimmte Player. Nur ändern, wenn dein Anbieter das verlangt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        OutlinedButton(onClick = {
            vm.clearCache()
            Toast.makeText(context, "Zwischenspeicher geleert", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.focusHighlight()) { Text("Zwischenspeicher leeren") }

        HorizontalDivider()
        Text("Über Marv TV", style = MaterialTheme.typography.titleMedium)
        Text(
            "Version 1.0\n© marween\n\n" +
                "Marv TV enthält selbst keine Sender. Die App spielt die Listen ab, die du hinzufügst, " +
                "sowie frei zugängliche öffentliche Sammlungen (iptv-org, Free-TV).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
