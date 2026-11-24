package de.marween.tv.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.marween.tv.AppViewModel
import de.marween.tv.Channel
import de.marween.tv.Kind
import de.marween.tv.PlayQueue
import de.marween.tv.Playlist

@Composable
fun FavoritesScreen(vm: AppViewModel, onOpenSeries: (Playlist, Channel) -> Unit) {
    val context = LocalContext.current
    val favs = vm.favorites.toList()
    val recents = vm.recents.toList()

    fun open(list: List<Channel>, i: Int) {
        val ch = list[i]
        if (ch.kind == Kind.SERIES) {
            val p = vm.playlists.find { it.id == ch.playlistId }
            if (p != null) onOpenSeries(p, ch)
            else Toast.makeText(context, "Die zugehörige Liste wurde gelöscht.", Toast.LENGTH_SHORT).show()
        } else {
            PlayQueue.start(context, list, i)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                "Favoriten",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        if (favs.isEmpty()) {
            item {
                Text(
                    "Noch keine Favoriten. Wähle bei einem Sender, Film oder einer Serie das Herz aus.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(6.dp),
                )
            }
        } else {
            itemsIndexed(favs) { i, ch ->
                ChannelRow(ch, isFavorite = true, onClick = { open(favs, i) }, onToggleFavorite = { vm.toggleFavorite(ch) })
            }
        }

        item {
            Row(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Zuletzt gesehen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (recents.isNotEmpty()) {
                    TextButton(onClick = { vm.clearRecents() }, modifier = Modifier.focusHighlight()) { Text("Verlauf löschen") }
                }
            }
        }
        if (recents.isEmpty()) {
            item {
                Text("Noch nichts angesehen.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(6.dp))
            }
        } else {
            itemsIndexed(recents) { i, ch ->
                ChannelRow(ch, isFavorite = vm.isFavorite(ch), onClick = { open(recents, i) }, onToggleFavorite = { vm.toggleFavorite(ch) })
            }
        }
    }
}
