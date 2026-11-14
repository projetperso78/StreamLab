package app.cleanroom.streamlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: the player client had a 30 s whole-call timeout, so live radios reconnected
 * every 30 s and stations with a pre-roll ad (NRJ, RMC, Nostalgie…) played the ad in a loop.
 */
class SecureNetworkTimeoutTest {
    @Test fun liveStreamsAreNeverCutByAWholeCallLimit() {
        assertEquals(0, SecureNetwork().playbackClient.callTimeoutMillis)
    }

    @Test fun stalledStreamsStillFail() {
        assertTrue(SecureNetwork().playbackClient.readTimeoutMillis in 1..30_000)
    }
}
