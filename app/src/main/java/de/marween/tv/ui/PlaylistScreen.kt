package de.marween.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.marween.tv.AppViewModel
import de.marween.tv.Channel
import de.marween.tv.Kind
import de.marween.tv.Loadable
import de.marween.tv.PlayQueue
import de.marween.tv.Playlist
import de.marween.tv.PlaylistContent
import de.marween.tv.PlaylistType
import de.marween.tv.Section
import de.marween.tv.newId

@Composable
fun PlaylistScreen(vm: AppViewModel, playlist: Playlist, onBack: () -> Unit, onOpenSeries: (Channel) -> Unit) {
    LaunchedEffect(playlist.id) { vm.open(playlist) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(playlist.name, onBack = onBack) {
            if (playlist.isPublic && !vm.isSaved(playlist.url)) {
                TextButton(
                    onClick = { vm.addPlaylist(playlist.copy(id = newId())) },
                    modifier = Modifier.focusHighlight(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Zu meinen Listen")
                }
            }
            IconButton(onClick = { vm.open(playlist, force = true) }, modifier = Modifier.focusHighlight(CircleShape)) {
                Icon(Icons.Default.Refresh, contentDescription = "Neu laden")
            }
        }

        val state = vm.content
        when {
            vm.contentFor != playlist.id || state is Loadable.Idle || state is Loadable.Loading ->
                LoadingView("Liste wird geladen …\nGroße Listen können etwas dauern.")
            state is Loadable.Error ->
                MessageView(state.message, actionLabel = "Erneut versuchen", onAction = { vm.open(playlist, force = true) })
            state is Loadable.Ready ->
                ContentView(vm, playlist, state.value, onOpenSeries)
        }
    }
}

@Composable
private fun ContentView(vm: AppViewModel, playlist: Playlist, content: PlaylistContent, onOpenSeries: (Channel) -> Unit) {
    val context = LocalContext.current
    var sectionIdx by rememberSaveable(playlist.id) { mutableIntStateOf(0) }
    val section: Section = content.sections.getOrNull(sectionIdx) ?: content.sections.first()
    var category by remember(playlist.id, sectionIdx) { mutableStateOf<String?>(null) }
    var query by remember(playlist.id, sectionIdx) { mutableStateOf("") }

    val counts = remember(section) { section.items.groupingBy { it.group }.eachCount() }
    val canCheck = playlist.type == PlaylistType.M3U && section.kind == Kind.LIVE
    var onlyReachable by remember(playlist.id) { mutableStateOf(true) }
    val statusCount = vm.streamStatus.size
    val visible = remember(section, category, query) {
        section.items.filter { (category == null || it.group == category) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
    }
    val filtered = remember(visible, onlyReachable, statusCount, canCheck) {
        if (canCheck && onlyReachable) visible.filter { vm.streamStatus[it.url] != false } else visible
    }

    val onItemClick: (Int) -> Unit = { i ->
        val ch = filtered[i]
        if (ch.kind == Kind.SERIES) onOpenSeries(ch) else PlayQueue.start(context, filtered, i)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (content.info.isNotBlank()) {
            Text(
                content.info,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }
        if (content.sections.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                content.sections.forEachIndexed { i, s ->
                    FilterChip(
                        selected = i == sectionIdx,
                        onClick = { sectionIdx = i },
                        label = { Text("${s.title} (${s.items.size})") },
                        modifier = Modifier.focusHighlight(),
                    )
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("In „${section.title}“ suchen …") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Leeren") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (canCheck) {
            CheckBar(vm, visible, onlyReachable) { onlyReachable = it }
        }
        Spacer(Modifier.padding(4.dp))

        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 700.dp) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.width(260.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item { CategoryItem("Alle", section.items.size, category == null) { category = null } }
                        items(section.categories) { c -> CategoryItem(c, counts[c] ?: 0, category == c) { category = c } }
                    }
                    Spacer(Modifier.width(10.dp))
                    ItemList(vm, filtered, section, Modifier.weight(1f), onItemClick)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        item {
                            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Alle") }, modifier = Modifier.focusHighlight())
                        }
                        items(section.categories) { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c, maxLines = 1) }, modifier = Modifier.focusHighlight())
                        }
                    }
                    ItemList(vm, filtered, section, Modifier.weight(1f), onItemClick)
                }
            }
        }
    }
}

@Composable
fun CheckBar(
    vm: AppViewModel,
    list: List<Channel>,
    onlyReachable: Boolean,
    showFilter: Boolean = true,
    onOnlyReachable: (Boolean) -> Unit,
) {
    val progress = vm.checkProgress
    val checked = list.count { vm.streamStatus.containsKey(it.url) }
    val ok = list.count { vm.streamStatus[it.url] == true }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (progress == null) {
                OutlinedButton(
                    onClick = { vm.checkStreams(list.take(2000), recheck = checked > 0) },
                    modifier = Modifier.focusHighlight(),
                ) { Text(if (checked == 0) "Sender prüfen" else "Erneut prüfen") }
            } else {
                OutlinedButton(onClick = { vm.cancelCheck() }, modifier = Modifier.focusHighlight()) { Text("Prüfung abbrechen") }
            }
            if (checked > 0 && showFilter) {
                FilterChip(
                    selected = onlyReachable,
                    onClick = { onOnlyReachable(!onlyReachable) },
                    label = { Text("Nur erreichbare") },
                    modifier = Modifier.focusHighlight(),
                )
            }
            if (checked > 0) {
                Text(
                    "$ok von $checked erreichbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                "Prüfe Sender … ${progress.first} von ${progress.second}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun statusLabel(vm: AppViewModel, ch: Channel): String = when (vm.streamStatus[ch.url]) {
    true -> "✓ erreichbar · "
    false -> "✗ offline · "
    null -> ""
}

@Composable
private fun CategoryItem(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ItemList(vm: AppViewModel, list: List<Channel>, section: Section, modifier: Modifier, onItemClick: (Int) -> Unit) {
    if (list.isEmpty()) {
        Column(modifier) { MessageView("Keine Treffer.") }
        return
    }
    LazyColumn(modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(list) { i, ch ->
            ChannelRow(
                ch,
                isFavorite = vm.isFavorite(ch),
                onClick = { onItemClick(i) },
                onToggleFavorite = { vm.toggleFavorite(ch) },
                subtitle = statusLabel(vm, ch) + if (section.kind == Kind.LIVE) "${i + 1} · ${ch.group}" else ch.group,
            )
        }
    }
}
