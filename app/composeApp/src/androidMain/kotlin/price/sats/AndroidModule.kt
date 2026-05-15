package price.sats

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import price.sats.core.SatsPriceCore as NativeCore

/**
 * Binds the Android-flavoured implementations of cross-platform contracts.
 * Kept separate from the common module so iOS can supply its own without
 * pulling JNA or SharedPreferences into the bundle.
 */
val androidModule = module {
    single<PriceCore> { AndroidPriceCore(NativeCore()) }
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("satsprice-prefs", Context.MODE_PRIVATE)
        )
    }
}
