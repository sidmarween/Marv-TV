package de.marween.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.marween.tv.ui.DiscoverScreen
import de.marween.tv.ui.FavoritesScreen
import de.marween.tv.ui.HomeScreen
import de.marween.tv.ui.PlaylistScreen
import de.marween.tv.ui.SeriesScreen
import de.marween.tv.ui.SettingsScreen
import de.marween.tv.ui.focusHighlight

sealed interface Screen {
    data object Home : Screen
    data object Discover : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data class PlaylistView(val playlist: Playlist) : Screen
    data class Series(val playlist: Playlist, val series: Channel) : Screen
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Home, "Meine Listen", Icons.Default.Home),
    Tab(Screen.Discover, "Entdecken", Icons.Default.Search),
    Tab(Screen.Favorites, "Favoriten", Icons.Default.Favorite),
    Tab(Screen.Settings, "Einstellungen", Icons.Default.Settings),
)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            MarvTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshLists()
    }
}

@Composable
fun MarvTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF4FC3F7),
        onPrimary = Color(0xFF00131F),
        secondary = Color(0xFFFFC857),
        background = Color(0xFF0E1116),
        surface = Color(0xFF151A22),
        surfaceVariant = Color(0xFF222A36),
        onSurfaceVariant = Color(0xFFAEB8C6),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun App(vm: AppViewModel) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = stack.last()
    val root = stack.first()

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun switchTab(s: Screen) { stack.clear(); stack.add(s) }

    BackHandler(enabled = stack.size > 1 || root != Screen.Home) {
        if (stack.size > 1) back() else switchTab(Screen.Home)
    }

    val content: @Composable () -> Unit = {
        when (current) {
            Screen.Home -> HomeScreen(vm, onOpen = { go(Screen.PlaylistView(it)) })
            Screen.Discover -> DiscoverScreen(vm, onOpenList = { go(Screen.PlaylistView(it)) })
            Screen.Favorites -> FavoritesScreen(vm, onOpenSeries = { p, s -> go(Screen.Series(p, s)) })
            Screen.Settings -> SettingsScreen(vm)
            is Screen.PlaylistView -> PlaylistScreen(
                vm, current.playlist,
                onBack = { back() },
                onOpenSeries = { go(Screen.Series(current.playlist, it)) },
            )
            is Screen.Series -> SeriesScreen(vm, current.playlist, current.series, onBack = { back() })
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = root == tab.screen,
                            onClick = { switchTab(tab.screen) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.focusHighlight(),
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) { content() }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = root == tab.screen,
                                onClick = { switchTab(tab.screen) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, maxLines = 1) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) { content() }
            }
        }
    }
}
