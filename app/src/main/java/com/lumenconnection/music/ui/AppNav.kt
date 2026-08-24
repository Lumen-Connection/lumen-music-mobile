package com.lumenconnection.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.ui.screens.AddMusicScreen
import com.lumenconnection.music.ui.screens.FolderDetailScreen
import com.lumenconnection.music.ui.screens.HomeScreen
import com.lumenconnection.music.ui.screens.LibraryScreen
import com.lumenconnection.music.ui.screens.LikedScreen
import com.lumenconnection.music.ui.screens.PlaylistsScreen
import com.lumenconnection.music.ui.screens.SearchScreen
import com.lumenconnection.music.ui.screens.SettingsScreen
import com.lumenconnection.music.ui.screens.SyncScreen
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.launch

/**
 * Rotas do app. A ordem espelha a barra lateral do desktop; a diferença
 * sancionada é que as três principais moram numa bottom navigation e o resto vem
 * pelo drawer (decisão registrada na §2 do PLANEJAMENTO).
 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val LIKED = "liked"
    const val PLAYLISTS = "playlists"
    const val FOLDER = "folder/{folderId}"
    const val ADD_MUSIC = "add"
    const val SYNC = "sync"
    const val SETTINGS = "settings"

    fun folder(id: Long) = "folder/$id"
}

private data class NavEntry(val route: String, val icon: ImageVector, val labelRes: Int)

private val BottomEntries = listOf(
    NavEntry(Routes.HOME, Icons.Default.Home, R.string.nav_home),
    NavEntry(Routes.SEARCH, Icons.Default.Search, R.string.nav_search),
    NavEntry(Routes.LIBRARY, Icons.AutoMirrored.Filled.LibraryBooks, R.string.nav_library),
)

private val DrawerEntries = listOf(
    NavEntry(Routes.HOME, Icons.Default.Home, R.string.nav_home),
    NavEntry(Routes.PLAYLISTS, Icons.AutoMirrored.Filled.QueueMusic, R.string.nav_playlists),
    NavEntry(Routes.LIKED, Icons.Default.Favorite, R.string.nav_liked),
    NavEntry(Routes.LIBRARY, Icons.AutoMirrored.Filled.LibraryBooks, R.string.nav_library),
    NavEntry(Routes.ADD_MUSIC, Icons.Default.Add, R.string.nav_add_music),
    NavEntry(Routes.SYNC, Icons.Default.Sync, R.string.nav_sync),
    NavEntry(Routes.SETTINGS, Icons.Default.Settings, R.string.nav_settings),
)

@Composable
fun AppNav() {
    val colors = LumenTheme.colors
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.sidebar,
                drawerContentColor = colors.text,
            ) {
                DrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navigateTop(nav, route)
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(colors.app)) {
            TopBar(onMenu = { scope.launch { drawerState.open() } })

            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.HOME) { HomeScreen(nav) }
                    composable(Routes.SEARCH) { SearchScreen(nav) }
                    composable(Routes.LIBRARY) { LibraryScreen(nav) }
                    composable(Routes.LIKED) { LikedScreen(nav) }
                    composable(Routes.PLAYLISTS) { PlaylistsScreen(nav) }
                    composable(
                        Routes.FOLDER,
                        arguments = listOf(navArgument("folderId") { type = NavType.LongType }),
                    ) { entry ->
                        FolderDetailScreen(nav, entry.arguments?.getLong("folderId") ?: 0L)
                    }
                    composable(Routes.ADD_MUSIC) { AddMusicScreen(nav) }
                    composable(Routes.SYNC) { SyncScreen(nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                }
            }

            BottomBar(currentRoute = currentRoute, onNavigate = { navigateTop(nav, it) })
        }
    }
}

/**
 * Navegação entre destinos de topo: mantém uma única instância na pilha e
 * preserva o estado de cada aba, que é o equivalente do histórico do desktop
 * (`MainWindow::navigateTo` não empilha refresh do mesmo destino).
 */
private fun navigateTop(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TopBar(onMenu: () -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.app)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Menu,
            contentDescription = stringResource(R.string.nav_settings),
            tint = colors.text,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(dimens.radiusWidget))
                .clickable(onClick = onMenu)
                .padding(8.dp),
        )
        Spacer(Modifier.width(dimens.spacingSm))
        Text(stringResource(R.string.app_name), style = LumenText.body)
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    Column {
        LumenDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.sidebar)
                .navigationBarsPadding()
                // Altura fixa: com `fillMaxHeight` nos filhos, a barra reivindica
                // todo o espaço livre da Column e esmaga a área de conteúdo.
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomEntries.forEach { entry ->
                val selected = currentRoute == entry.route
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onNavigate(entry.route) }
                        .padding(vertical = dimens.spacingSm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = if (selected) colors.accent else colors.muted,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        stringResource(entry.labelRes),
                        style = LumenText.micro.copy(
                            color = if (selected) colors.accent else colors.muted,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Conteúdo do drawer: marca no topo, itens na mesma ordem da barra lateral do
 * desktop e o rodapé com a contagem de faixas (`%1 faixa%2 na biblioteca`).
 */
@Composable
private fun DrawerContent(currentRoute: String?, onNavigate: (String) -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val trackCount by Graph.db.trackDao().observeCount().collectAsStateWithLifecycle(initialValue = 0)

    Column(Modifier.fillMaxSize().padding(dimens.spacing)) {
        Row(
            Modifier.padding(vertical = dimens.spacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Image(
                painter = painterResource(R.drawable.logo_lumen_music),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(36.dp),
            )
            Column {
                Text(stringResource(R.string.app_name), style = LumenText.body)
                Text("Lumen Connection", style = LumenText.micro)
            }
        }

        LumenDivider()
        Spacer(Modifier.height(dimens.spacingSm))

        DrawerEntries.forEach { entry ->
            val selected = currentRoute == entry.route
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(dimens.navItemHeight)
                    .clip(RoundedCornerShape(dimens.radiusWidget))
                    .background(if (selected) colors.cardHover else Color.Transparent)
                    .clickable { onNavigate(entry.route) }
                    .padding(horizontal = dimens.spacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
            ) {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    tint = if (selected) colors.accent else colors.muted,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(entry.labelRes),
                    style = LumenText.body.copy(
                        color = if (selected) colors.accent else colors.text,
                    ),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        LumenDivider()
        Text(
            pluralStringResource(R.plurals.track_count_in_library, trackCount, trackCount),
            style = LumenText.micro,
            modifier = Modifier.padding(top = dimens.spacingSm),
        )
    }
}
