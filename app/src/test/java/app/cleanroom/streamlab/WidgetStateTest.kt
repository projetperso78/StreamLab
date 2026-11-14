package app.cleanroom.streamlab

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStateTest {
    @Test fun toggleSendsPauseWhilePlayingAndPlayOtherwise() {
        assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, WidgetState.toggleKey(true))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, WidgetState.toggleKey(false))
    }

    @Test fun titleDropsTheNearYouPinAndFallsBackWhenEmpty() {
        assertEquals("Radio Atlas", WidgetState.title("📍 Radio Atlas", "idle"))
        assertEquals("idle", WidgetState.title(null, "idle"))
        assertEquals("idle", WidgetState.title("   ", "idle"))
        assertEquals(80, WidgetState.title("x".repeat(200), "idle").length)
    }
}
