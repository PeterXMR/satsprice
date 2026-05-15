package price.sats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Which field the user last edited — the canonical sats value is derived from
 * this single source so the other fields can project off it without circular
 * "field A updates field B updates field A" loops.
 */
sealed interface InputSource {
    object Sats : InputSource
    object Btc : InputSource
    data class Fiat(val code: String) : InputSource
}

/**
 * Which Bitcoin denomination is currently editable. Sticky across input
 * source changes so that switching to a fiat and back doesn't lose the user's
 * preferred unit.
 */
enum class BitcoinUnit { SATS, BTC }

/**
 * Holds the converter's state across configuration changes (rotation, locale
 * switch, dark-mode toggle, etc.) and runs the 65s background refresh tick.
 *
 * Persists three things across process restart via [PreferencesRepo]:
 *  - the set of selected fiats
 *  - which field the user was last editing
 *  - the amount in that field
 *
 * The tick is 65s rather than 60s on purpose — the Rust core cache has a 60s
 * TTL, so a 60s poll would race the cache boundary and sometimes return a
 * stale snapshot. 65s guarantees the cache has expired by the time we ask.
 */
class ConverterViewModel(
    private val core: PriceCore,
    private val prefs: PreferencesRepo,
) : ViewModel() {

    val selectedFiats = mutableStateListOf<String>().apply { addAll(prefs.selectedFiats()) }
    val snapshots = mutableStateMapOf<String, PriceSnapshot>()
    val loadingFiats = mutableStateMapOf<String, Boolean>()
    val errorFiats = mutableStateMapOf<String, String>()

    var inputSource: InputSource by mutableStateOf(prefs.inputSource())
        private set
    var inputAmount: String by mutableStateOf(prefs.inputAmount())
        private set

    /** null = follow system, true = forced dark, false = forced light. */
    var themeOverride: Boolean? by mutableStateOf(prefs.themeOverride())
        private set

    /** Which Bitcoin unit's field is currently editable. */
    var bitcoinUnit: BitcoinUnit by mutableStateOf(prefs.bitcoinUnit())
        private set

    val supportedFiats: List<String> = core.supportedFiats()

    val isAnyLoading: Boolean
        get() = loadingFiats.any { it.value }

    init {
        viewModelScope.launch {
            selectedFiats.forEach { fiat ->
                if (snapshots[fiat] == null) loadPrice(fiat)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refreshAll()
            }
        }
    }

    fun setInput(source: InputSource, amount: String) {
        inputSource = source
        inputAmount = amount
        prefs.setInputSource(source)
        prefs.setInputAmount(amount)
        // Typing directly into a Bitcoin field also defines the editable unit.
        when (source) {
            InputSource.Sats -> if (bitcoinUnit != BitcoinUnit.SATS) {
                bitcoinUnit = BitcoinUnit.SATS
                prefs.setBitcoinUnit(BitcoinUnit.SATS)
            }
            InputSource.Btc -> if (bitcoinUnit != BitcoinUnit.BTC) {
                bitcoinUnit = BitcoinUnit.BTC
                prefs.setBitcoinUnit(BitcoinUnit.BTC)
            }
            is InputSource.Fiat -> {} // bitcoinUnit stays whatever it was
        }
    }

    fun toggleFiat(fiat: String) {
        if (selectedFiats.contains(fiat)) {
            if (selectedFiats.size > 1) {
                selectedFiats.remove(fiat)
                prefs.setSelectedFiats(selectedFiats.toList())
            }
        } else {
            selectedFiats.add(fiat)
            prefs.setSelectedFiats(selectedFiats.toList())
            viewModelScope.launch { loadPrice(fiat) }
        }
    }

    fun removeFiat(fiat: String) {
        if (selectedFiats.size <= 1) return
        if ((inputSource as? InputSource.Fiat)?.code == fiat) {
            val current = computeSats(core, inputSource, inputAmount, snapshots)
            inputSource = InputSource.Sats
            inputAmount = current?.toString() ?: "100000000"
            prefs.setInputSource(inputSource)
            prefs.setInputAmount(inputAmount)
        }
        selectedFiats.remove(fiat)
        prefs.setSelectedFiats(selectedFiats.toList())
    }

    fun refreshAll() {
        viewModelScope.launch {
            selectedFiats.forEach { launch { loadPrice(it) } }
        }
    }

    /**
     * Toggle between explicit light and dark, ignoring the (unspecified)
     * "follow system" state. Once toggled the choice sticks until the user
     * clears it from a future settings screen.
     */
    fun toggleTheme(systemDark: Boolean) {
        val current = themeOverride ?: systemDark
        val next = !current
        themeOverride = next
        prefs.setThemeOverride(next)
    }

    /**
     * Switch which Bitcoin unit's field is editable. Sets the input source
     * to that unit and converts the current canonical sats value into the
     * new field's representation so the user sees the same amount in the
     * new unit immediately.
     */
    fun selectBitcoinUnit(unit: BitcoinUnit) {
        if (bitcoinUnit == unit && inputSource is InputSource.Sats && unit == BitcoinUnit.SATS) return
        if (bitcoinUnit == unit && inputSource is InputSource.Btc && unit == BitcoinUnit.BTC) return
        val sats = computeSats(core, inputSource, inputAmount, snapshots)
        bitcoinUnit = unit
        prefs.setBitcoinUnit(unit)
        when (unit) {
            BitcoinUnit.SATS -> {
                inputSource = InputSource.Sats
                inputAmount = sats?.toString() ?: "0"
            }
            BitcoinUnit.BTC -> {
                inputSource = InputSource.Btc
                inputAmount = sats?.let { core.convertSatsToBtc(it) } ?: "0"
            }
        }
        prefs.setInputSource(inputSource)
        prefs.setInputAmount(inputAmount)
    }

    fun displayedSats(): String =
        if (inputSource == InputSource.Sats) inputAmount
        else computeSats(core, inputSource, inputAmount, snapshots)?.toString().orEmpty()

    fun displayedBtc(): String =
        if (inputSource == InputSource.Btc) inputAmount
        else computeSats(core, inputSource, inputAmount, snapshots)
            ?.let { core.convertSatsToBtc(it) }.orEmpty()

    fun displayedFiat(fiat: String): String {
        if ((inputSource as? InputSource.Fiat)?.code == fiat) return inputAmount
        val sats = computeSats(core, inputSource, inputAmount, snapshots) ?: return ""
        val price = snapshots[fiat]?.price ?: return ""
        return runCatching { core.convertSatsToFiat(sats, price) }.getOrDefault("")
    }

    private suspend fun loadPrice(fiat: String) {
        loadingFiats[fiat] = true
        errorFiats.remove(fiat)
        try {
            snapshots[fiat] = core.fetchPrice(fiat)
        } catch (t: Throwable) {
            errorFiats[fiat] = t.message ?: t::class.simpleName ?: "fetch failed"
        } finally {
            loadingFiats[fiat] = false
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 65_000L
    }
}

internal fun computeSats(
    core: PriceCore,
    source: InputSource,
    raw: String,
    snapshots: Map<String, PriceSnapshot>,
): ULong? {
    if (raw.isBlank()) return null
    return runCatching {
        when (source) {
            InputSource.Sats -> raw.toULongOrNull()
            InputSource.Btc -> core.convertBtcToSats(raw)
            is InputSource.Fiat -> {
                val price = snapshots[source.code]?.price ?: return null
                core.convertFiatToSats(raw, price)
            }
        }
    }.getOrNull()
}
