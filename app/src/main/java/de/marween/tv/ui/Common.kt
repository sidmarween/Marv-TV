package de.marween.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.marween.tv.AppViewModel
import de.marween.tv.Channel
import de.marween.tv.Epg
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import de.marween.tv.Kind

/** Deutlich sichtbarer Fokus-Rahmen für die Bedienung mit der Fernbedienung. */
@Composable
fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(12.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "fokus")
    val color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(3.dp, color, shape)
        .onFocusChanged { focused = it.hasFocus }
}

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.focusHighlight(CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        actions()
    }
}

@Composable
fun Logo(url: String, name: String, size: Dp) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                initials(name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }
    }
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.take(1).uppercase() }.ifEmpty { "TV" }

/** Aktuelle Uhrzeit für das TV-Programm; aktualisiert sich jede halbe Minute und wenn neues EPG geladen wurde. */
@Composable
fun rememberEpgNow(vm: AppViewModel): Long {
    val tick by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val version = vm.epgVersion
    return remember(tick, version) { System.currentTimeMillis() }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)
private val dayFormat = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY)

fun formatTime(ms: Long): String = timeFormat.format(Date(ms))

@Composable
fun ChannelRow(
    ch: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    subtitle: String = ch.group,
    now: Long = 0L,
) {
    val showEpg = now > 0 && ch.kind == Kind.LIVE
    val (cur, next) = if (showEpg) Epg.nowNext(ch, now) else (null to null)
    val logo = ch.logo.ifBlank { if (showEpg) Epg.icon(ch) ?: "" else "" }
    var showGuide by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.weight(1f).focusHighlight(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(logo, ch.name, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ch.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cur != null) {
                        Text(
                            "${formatTime(cur.start)}  ${cur.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val span = (cur.end - cur.start).coerceAtLeast(1)
                        LinearProgressIndicator(
                            progress = { ((now - cur.start).toFloat() / span).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).height(3.dp),
                        )
                    }
                    if (next != null) {
                        Text(
                            "Danach ${formatTime(next.start)}  ${next.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    if (ch.kind == Kind.SERIES) Icons.Default.List else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (cur != null || next != null) {
            IconButton(onClick = { showGuide = true }, modifier = Modifier.focusHighlight(CircleShape)) {
                Icon(Icons.Default.DateRange, contentDescription = "TV-Programm")
            }
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.focusHighlight(CircleShape)) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                    tint = if (isFavorite) Color(0xFFFF5C7A) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showGuide) {
        ProgrammeDialog(ch, now, onPlay = { showGuide = false; onClick() }, onDismiss = { showGuide = false })
    }
}

/** TV-Programm eines Senders (ab der laufenden Sendung). */
@Composable
fun ProgrammeDialog(ch: Channel, now: Long, onPlay: () -> Unit, onDismiss: () -> Unit) {
    val list = remember(ch, now) { Epg.programmes(ch).filter { it.end > now } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TV-Programm: ${ch.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            if (list.isEmpty()) {
                Text("Kein TV-Programm vorhanden.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(list) { i, p ->
                        val day = dayFormat.format(Date(p.start))
                        val prevDay = list.getOrNull(i - 1)?.let { dayFormat.format(Date(it.start)) }
                        Column {
                            if (i == 0 || day != prevDay) {
                                Text(
                                    day,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                )
                            }
                            val running = p.start <= now
                            Text(
                                "${formatTime(p.start)} – ${formatTime(p.end)}   ${p.title}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (running) FontWeight.Bold else FontWeight.Normal,
                                color = if (running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (p.desc.isNotBlank()) {
                                Text(
                                    p.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onPlay, modifier = Modifier.focusHighlight()) { Text("Ansehen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight()) { Text("Schließen") }
        },
    )
}

@Composable
fun LoadingView(text: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MessageView(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction, modifier = Modifier.focusHighlight()) { Text(actionLabel) }
        }
    }
}
