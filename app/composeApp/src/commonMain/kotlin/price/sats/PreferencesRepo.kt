package price.sats

import com.russhwolf.settings.Settings

/**
 * Thin facade over [Settings] that converts between domain types and the
 * String/Int/Long primitives the underlying KV stores expose. Keeping this
 * single-purpose makes the ViewModel agnostic to the backing platform.
 *
 * Storage layout:
 *  - `selectedFiats`  → comma-joined ISO codes, e.g. `"usd,eur,pyg"`
 *  - `inputAmount`    → raw user-entered string (preserves scale / leading zeros)
 *  - `inputSource`    → `"sats"`, `"btc"`, or `"fiat:<code>"`
 */
class PreferencesRepo(private val settings: Settings) {

    fun selectedFiats(): List<String> =
        settings.getStringOrNull(KEY_FIATS)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("usd")

    fun setSelectedFiats(fiats: List<String>) {
        settings.putString(KEY_FIATS, fiats.joinToString(","))
    }

    fun inputAmount(): String =
        settings.getStringOrNull(KEY_INPUT_AMOUNT) ?: "100000000"

    fun setInputAmount(amount: String) {
        settings.putString(KEY_INPUT_AMOUNT, amount)
    }

    fun inputSource(): InputSource =
        when (val raw = settings.getStringOrNull(KEY_INPUT_SOURCE)) {
            null, "sats" -> InputSource.Sats
            "btc" -> InputSource.Btc
            else -> if (raw.startsWith("fiat:")) InputSource.Fiat(raw.removePrefix("fiat:"))
                    else InputSource.Sats
        }

    fun setInputSource(source: InputSource) {
        val raw = when (source) {
            InputSource.Sats -> "sats"
            InputSource.Btc -> "btc"
            is InputSource.Fiat -> "fiat:${source.code}"
        }
        settings.putString(KEY_INPUT_SOURCE, raw)
    }

    /** null = follow system, true = forced dark, false = forced light. */
    fun themeOverride(): Boolean? = settings.getBooleanOrNull(KEY_THEME_DARK)

    fun setThemeOverride(dark: Boolean?) {
        if (dark == null) settings.remove(KEY_THEME_DARK)
        else settings.putBoolean(KEY_THEME_DARK, dark)
    }

    fun bitcoinUnit(): BitcoinUnit =
        if (settings.getStringOrNull(KEY_BTC_UNIT) == "btc") BitcoinUnit.BTC
        else BitcoinUnit.SATS

    fun setBitcoinUnit(unit: BitcoinUnit) {
        settings.putString(KEY_BTC_UNIT, if (unit == BitcoinUnit.BTC) "btc" else "sats")
    }

    private companion object {
        const val KEY_FIATS = "selectedFiats"
        const val KEY_INPUT_AMOUNT = "inputAmount"
        const val KEY_INPUT_SOURCE = "inputSource"
        const val KEY_THEME_DARK = "themeDark"
        const val KEY_BTC_UNIT = "bitcoinUnit"
    }
}
