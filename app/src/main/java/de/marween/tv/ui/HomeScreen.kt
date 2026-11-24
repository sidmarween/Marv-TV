package de.marween.tv.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.marween.tv.AppViewModel
import de.marween.tv.Playlist
import de.marween.tv.PlaylistType
import de.marween.tv.newId

@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (Playlist) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Meine Listen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { showAdd = true }, modifier = Modifier.focusHighlight()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Hinzufügen")
            }
        }
        Spacer(Modifier.height(12.dp))

        if (vm.playlists.isEmpty()) {
            MessageView(
                "Noch keine Listen vorhanden.\n\nFüge eine M3U-Liste oder deine Xtream-Zugangsdaten hinzu – " +
                    "oder schau unter „Entdecken“ nach frei verfügbaren Sendern.",
                actionLabel = "Liste hinzufügen",
                onAction = { showAdd = true },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.playlists, key = { it.id }) { p ->
                    PlaylistCard(p, onOpen = { onOpen(p) }, onEdit = { editing = p }, onDelete = { deleting = p })
                }
            }
        }
    }

    if (showAdd || editing != null) {
        AddPlaylistDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { p ->
                if (editing != null) vm.updatePlaylist(p) else vm.addPlaylist(p)
                showAdd = false
                editing = null
            },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Liste löschen?") },
            text = { Text("„${p.name}“ wird entfernt. Deine Favoriten bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaylist(p); deleting = null }, modifier = Modifier.focusHighlight()) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, modifier = Modifier.focusHighlight()) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun PlaylistCard(p: Playlist, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            onClick = onOpen,
            modifier = Modifier.weight(1f).focusHighlight(RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        p.isPublic -> Icons.Default.Star
                        p.type == PlaylistType.XTREAM -> Icons.Default.Lock
                        else -> Icons.Default.List
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        p.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.focusHighlight(CircleShape)) {
            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
        }
        IconButton(onClick = onDelete, modifier = Modifier.focusHighlight(CircleShape)) {
            Icon(Icons.Default.Delete, contentDescription = "Löschen")
        }
    }
}

private enum class AddMode(val label: String) { LINK("M3U-Link"), FILE("M3U-Datei"), XTREAM("Xtream Codes") }

@Composable
fun AddPlaylistDialog(initial: Playlist?, onDismiss: () -> Unit, onSave: (Playlist) -> Unit) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(
            when {
                initial?.type == PlaylistType.XTREAM -> AddMode.XTREAM
                initial?.url?.startsWith("content://") == true -> AddMode.FILE
                else -> AddMode.LINK
            }
        )
    }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val initUrl = initial?.url ?: ""
    val initType = initial?.type
    var link by remember { mutableStateOf(if (initType == PlaylistType.M3U && !initUrl.startsWith("content://")) initUrl else "") }
    var fileUri by remember { mutableStateOf(if (initUrl.startsWith("content://")) initUrl else "") }
    var server by remember { mutableStateOf(if (initType == PlaylistType.XTREAM) initUrl else "") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var pass by remember { mutableStateOf(initial?.password ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            fileUri = uri.toString()
            if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
        }
    }

    fun save() {
        error = null
        val id = initial?.id ?: newId()
        val result: Playlist = when (mode) {
            AddMode.LINK -> {
                val url = link.trim()
                if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                    error = "Bitte einen gültigen Link eingeben (beginnt mit http:// oder https://)."
                    return
                }
                // Xtream-Links (get.php?username=…&password=…) werden automatisch erkannt
                val uri = Uri.parse(url)
                val u = runCatching { uri.getQueryParameter("username") }.getOrNull()
                val pw = runCatching { uri.getQueryParameter("password") }.getOrNull()
                if (url.contains("get.php", true) && !u.isNullOrBlank() && !pw.isNullOrBlank()) {
                    Playlist(id, name.ifBlank { uri.host ?: "Xtream" }, PlaylistType.XTREAM, url.substringBefore("/get.php"), u, pw)
                } else {
                    Playlist(id, name.ifBlank { uri.host ?: "M3U-Liste" }, PlaylistType.M3U, url)
                }
            }
            AddMode.FILE -> {
                if (fileUri.isBlank()) { error = "Bitte eine Datei auswählen."; return }
                Playlist(id, name.ifBlank { "M3U-Datei" }, PlaylistType.M3U, fileUri)
            }
            AddMode.XTREAM -> {
                if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                    error = "Bitte Server, Benutzername und Passwort ausfüllen."
                    return
                }
                val s = server.trim()
                Playlist(id, name.ifBlank { Uri.parse(if (s.contains("://")) s else "http://$s").host ?: "Xtream" },
                    PlaylistType.XTREAM, s, user.trim(), pass.trim())
            }
        }
        onSave(result)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Liste hinzufügen" else "Liste bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AddMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m; error = null },
                            label = { Text(m.label) },
                            modifier = Modifier.focusHighlight(),
                        )
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (mode) {
                    AddMode.LINK -> {
                        OutlinedTextField(
                            value = link, onValueChange = { link = it },
                            label = { Text("M3U-Link") },
                            placeholder = { Text("https://…/liste.m3u") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Tipp: Ein Xtream-Link (get.php?username=…&password=…) wird automatisch als Xtream-Zugang " +
                                "gespeichert – dann gibt es auch Filme und Serien.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AddMode.FILE -> {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().focusHighlight(),
                        ) { Text(if (fileUri.isBlank()) "Datei auswählen …" else "Andere Datei auswählen …") }
                        if (fileUri.isNotBlank()) {
                            Text(
                                "Ausgewählt: ${Uri.parse(fileUri).lastPathSegment ?: fileUri}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    AddMode.XTREAM -> {
                        OutlinedTextField(
                            value = server, onValueChange = { server = it },
                            label = { Text("Server-Adresse") },
                            placeholder = { Text("http://server.de:8080") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = user, onValueChange = { user = it },
                            label = { Text("Benutzername") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = pass, onValueChange = { pass = it },
                            label = { Text("Passwort") }, singleLine = true,
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Verbergen" else "Zeigen") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { save() }, modifier = Modifier.focusHighlight()) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight()) { Text("Abbrechen") }
        },
    )
}
