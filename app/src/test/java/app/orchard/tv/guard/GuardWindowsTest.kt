package app.orchard.tv.guard

import org.junit.Assert.*
import org.junit.Test

class GuardWindowsTest {
    private val homes = mapOf("stock" to setOf("stock.Home"))
    private val transient = setOf("android", "systemui", "ime")
    private fun remember(previous: Pair<String, String>?, pkg: String, cls: String, full: Boolean = true) =
        rememberGuardWindow(previous, pkg, cls, full, homes, transient, "aerial")

    @Test fun systemAndKeyboardWindowsDoNotHideTheStockHomeDuringWake() {
        val home = "stock" to "stock.Home"
        for (pkg in transient) {
            val last = remember(home, pkg, "Popup")
            assertEquals(home, last)
            assertTrue(stockHomeInFront(last, homes))
        }
    }
    @Test fun doesNotTakeOverWhenAVideoAppIsInFrontOfTheStockHome() {
        val last = remember("stock" to "stock.Home", "player", "player.Playback")
        assertFalse(stockHomeInFront(last, homes))
        assertEquals(last, remember(last, "systemui", "Volume"))
        assertEquals(last, remember(last, "player", "SmallPopup", false))
    }
    @Test fun settingsWithinAStockPackageAreNotTreatedAsItsHomeActivity() {
        assertFalse(stockHomeInFront(remember(null, "stock", "stock.Settings"), homes))
        assertTrue(stockHomeInFront(remember(null, "stock", "stock.Home", false), homes))
        assertFalse(stockHomeInFront(remember(null, "aerial", "HistoryDialog", false), homes))
    }
}
