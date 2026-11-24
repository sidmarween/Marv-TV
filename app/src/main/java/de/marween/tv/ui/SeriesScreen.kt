package de.marween.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.marween.tv.AppViewModel
import de.marween.tv.Channel
import de.marween.tv.Loadable
import de.marween.tv.PlayQueue
import de.marween.tv.Playlist

@Composable
fun SeriesScreen(vm: AppViewModel, playlist: Playlist, series: Channel, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(series.key) { vm.loadEpisodes(playlist, series) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(series.name, onBack = onBack)
        Row(Modifier.padding(horizontal = 16.dp)) {
            Logo(series.logo, series.name, 96.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                series.plot.ifBlank { series.group },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (val state = vm.episodes) {
            is Loadable.Idle, is Loadable.Loading -> LoadingView("Folgen werden geladen …")
            is Loadable.Error -> MessageView(state.message, "Erneut versuchen") { vm.loadEpisodes(playlist, series) }
            is Loadable.Ready -> {
                val seasons = state.value
                if (seasons.isEmpty()) {
                    MessageView("Für diese Serie wurden keine Folgen gefunden.")
                } else {
                    var seasonIdx by remember(series.key) { mutableIntStateOf(0) }
                    val (_, episodes) = seasons.getOrElse(seasonIdx) { seasons.first() }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        itemsIndexed(seasons) { i, s ->
                            FilterChip(
                                selected = i == seasonIdx,
                                onClick = { seasonIdx = i },
                                label = { Text("${s.first} (${s.second.size})") },
                                modifier = Modifier.focusHighlight(),
                            )
                        }
                    }
                    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
                        itemsIndexed(episodes) { i, ep ->
                            ChannelRow(
                                ep,
                                isFavorite = false,
                                onClick = { PlayQueue.start(context, episodes, i, autoNext = true) },
                                onToggleFavorite = null,
                                subtitle = ep.plot.ifBlank { ep.group },
                            )
                        }
                    }
                }
            }
        }
    }
}
