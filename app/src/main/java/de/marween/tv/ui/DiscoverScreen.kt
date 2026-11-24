package de.marween.tv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.marween.tv.AppViewModel
import de.marween.tv.Loadable
import de.marween.tv.PlayQueue
import de.marween.tv.Playlist
import de.marween.tv.PublicSource
import de.marween.tv.PublicSources
import de.marween.tv.newId

@Composable
fun DiscoverScreen(vm: AppViewModel, onOpenList: (Playlist) -> Unit) {
    val context = LocalContext.current
    val results = vm.searchResults
    val q = vm.searchQuery.trim()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Entdecken",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Frei verfügbare, öffentliche Sender aus offenen Sammlungen (iptv-org, Free-TV).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Sender oder Liste suchen, z. B. „ZDF“ oder „Sport“") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchPublic() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.searchPublic() }, modifier = Modifier.focusHighlight()) { Text("Suchen") }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (results !is Loadable.Idle) {
                val matching = PublicSources.all.filter {
                    q.isNotEmpty() && (it.title.contains(q, true) || it.subtitle.contains(q, true))
                }.distinctBy { it.url }
                if (matching.isNotEmpty()) {
                    header("Passende Listen")
                    items(matching) { src -> SourceRow(vm, src, onOpenList) }
                }
                header("Passende Sender")
                when (results) {
                    is Loadable.Loading -> item {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(14.dp))
                            Text("Durchsuche über 10.000 öffentliche Sender …\nBeim ersten Mal kann das einen Moment dauern.")
                        }
                    }
                    is Loadable.Error -> item { Text(results.message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                    is Loadable.Ready -> {
                        val all = results.value
                        val list = all.filter { vm.streamStatus[it.url] != false }
                        if (all.isNotEmpty()) {
                            item { CheckBar(vm, all, onlyReachable = true, showFilter = false, onOnlyReachable = {}) }
                        }
                        if (list.isEmpty()) {
                            item { Text("Keine Sender gefunden.", modifier = Modifier.padding(16.dp)) }
                        } else {
                            item {
                                Text(
                                    if (list.size >= 500) "Die ersten 500 Treffer" else "${list.size} Treffer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                                )
                            }
                            itemsIndexed(list) { i, ch ->
                                ChannelRow(
                                    ch,
                                    isFavorite = vm.isFavorite(ch),
                                    onClick = { PlayQueue.start(context, list, i) },
                                    onToggleFavorite = { vm.toggleFavorite(ch) },
                                    subtitle = statusLabel(vm, ch) + ch.group,
                                )
                            }
                        }
                    }
                    else -> Unit
                }
                item {
                    TextButton(onClick = { vm.resetSearch() }, modifier = Modifier.padding(top = 8.dp).focusHighlight()) {
                        Text("Suche zurücksetzen")
                    }
                }
            } else {
                header("Empfohlen")
                items(PublicSources.featured) { SourceRow(vm, it, onOpenList) }
                header("Nach Kategorie")
                items(PublicSources.categories) { SourceRow(vm, it, onOpenList) }
                header("Weitere Länder")
                items(PublicSources.countries) { SourceRow(vm, it, onOpenList) }
                header("Große Sammlungen")
                items(PublicSources.collections) { SourceRow(vm, it, onOpenList) }
                item {
                    Text(
                        "Hinweis: Die öffentlichen Listen werden von Freiwilligen gepflegt – viele Links sind veraltet " +
                            "oder zeitweise offline. Mit „Sender prüfen“ in einer Liste blendest du nicht erreichbare Sender aus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, start = 6.dp, end = 6.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.header(text: String) {
    item {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun SourceRow(vm: AppViewModel, src: PublicSource, onOpenList: (Playlist) -> Unit) {
    val saved = vm.isSaved(src.url)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(
            onClick = { onOpenList(src.asPlaylist()) },
            modifier = Modifier.weight(1f).focusHighlight(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(src.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    src.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (saved) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Bereits in meinen Listen",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            IconButton(
                onClick = { vm.addPlaylist(src.asPlaylist().copy(id = newId())) },
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zu meinen Listen hinzufügen")
            }
        }
    }
}
