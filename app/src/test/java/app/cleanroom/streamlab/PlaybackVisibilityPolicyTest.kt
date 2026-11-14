package app.cleanroom.streamlab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVisibilityPolicyTest {
    @Test fun resumesOnlyWhenPlaybackWasActiveBeforeTheScreenWasHidden() {
        val policy = PlaybackVisibilityPolicy()
        policy.onPause(wasPlaying = true)
        assertTrue(policy.onResume())
        assertFalse(policy.onResume())

        policy.onPause(wasPlaying = false)
        assertFalse(policy.onResume())
    }
}
