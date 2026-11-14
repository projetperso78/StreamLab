package app.cleanroom.streamlab.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerPolicyTest {
    @Test fun phoneCommandRequiresBothOwnPackageAndUid() {
        assertTrue(ControllerPolicy.canPlaySource("app.cleanroom.streamlab", 1001, "app.cleanroom.streamlab", 1001))
        assertFalse(ControllerPolicy.canPlaySource("other.app", 1002, "app.cleanroom.streamlab", 1001))
        assertFalse(ControllerPolicy.canPlaySource("app.cleanroom.streamlab", 1002, "app.cleanroom.streamlab", 1001))
        assertFalse(ControllerPolicy.canPlaySource("other.app", 1001, "app.cleanroom.streamlab", 1001))
    }

    @Test fun passengerCompanionRequiresExpectedPackageAndMatchingSignature() {
        assertTrue(ControllerPolicy.isTrustedPassengerCompanion("app.cleanroom.streamlab.car", "app.cleanroom.streamlab", true))
        assertFalse(ControllerPolicy.isTrustedPassengerCompanion("app.cleanroom.streamlab.car", "app.cleanroom.streamlab", false))
        assertFalse(ControllerPolicy.isTrustedPassengerCompanion("com.google.android.projection.gearhead", "app.cleanroom.streamlab", true))
        assertFalse(ControllerPolicy.isTrustedPassengerCompanion("other.app", "app.cleanroom.streamlab", true))
    }
}
