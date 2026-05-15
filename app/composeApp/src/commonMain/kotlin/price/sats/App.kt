package price.sats

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val vm: ConverterViewModel = koinViewModel()
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = vm.themeOverride ?: systemDark
    MaterialTheme(
        colorScheme = if (effectiveDark) darkColorScheme() else lightColorScheme(),
    ) {
        ConverterScreen(vm)
    }
}
