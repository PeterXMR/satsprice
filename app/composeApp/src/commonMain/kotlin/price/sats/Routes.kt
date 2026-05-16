package price.sats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Nav route ids as plain string constants. Deliberately NOT a sealed class
 * with nested objects + a companion `all` list — that pattern hits a Kotlin
 * class-init-order trap where the companion's list literal resolves before
 * the nested objects' supers finish, producing null ids at first read.
 */
object Routes {
    const val Converter = "converter"
    const val Sources = "sources"
    const val Settings = "settings"
    const val About = "about"
}

data class DrawerItem(val route: String, val label: String, val icon: ImageVector)

val drawerItems: List<DrawerItem> = listOf(
    DrawerItem(Routes.Converter, "Converter", Icons.Filled.CurrencyExchange),
    DrawerItem(Routes.Sources, "Price sources", Icons.Filled.Storage),
    DrawerItem(Routes.Settings, "Settings", Icons.Filled.Settings),
    DrawerItem(Routes.About, "About", Icons.Filled.Info),
)
