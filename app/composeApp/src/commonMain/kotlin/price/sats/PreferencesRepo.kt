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

    private companion object {
        const val KEY_FIATS = "selectedFiats"
        const val KEY_INPUT_AMOUNT = "inputAmount"
        const val KEY_INPUT_SOURCE = "inputSource"
    }
}
