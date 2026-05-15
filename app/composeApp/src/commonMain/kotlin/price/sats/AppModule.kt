package price.sats

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Platform-agnostic Koin module. The platform module (androidMain / iosMain)
 * is responsible for binding [PriceCore] and `com.russhwolf.settings.Settings`
 * to their concrete implementations.
 */
val appModule = module {
    single { PreferencesRepo(get()) }
    viewModel { ConverterViewModel(get(), get()) }
}
